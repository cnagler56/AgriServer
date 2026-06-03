package com.home.Service;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.annotation.PostConstruct;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.home.Domain.ForecastLocation;
import com.home.Repository.ForecastLocationRepository;

/**
 * Tracks per-location NWS forecast snapshots and computes day-by-day deltas
 * between the previous snapshot and the current one.
 *
 * Snapshot format (stored in DB as JSON):
 *   { "days": [
 *       {"day":"Today","high":78,"low":58,"precipChance":10,"shortForecast":"Partly Sunny"},
 *       ...
 *   ] }
 */
@Service
public class ForecastChangeService {

	private static final String UA =
		"AgriServer/1.0 (educational project) — rebeccanagelmg@gmail.com";

	private final RestTemplate restTemplate;
	private final ForecastLocationRepository repo;
	private final ObjectMapper mapper = new ObjectMapper();

	public ForecastChangeService(RestTemplate restTemplate, ForecastLocationRepository repo) {
		this.restTemplate = restTemplate;
		this.repo = repo;
	}

	/** Seed five default Midwest locations on first startup. */
	@PostConstruct
	void seedDefaults() {
		if (repo.count() > 0) return;
		List<ForecastLocation> seeds = List.of(
			make("Winnebago, MN",    43.7686, -94.1655),
			make("Brookings, SD",    44.3114, -96.7898),
			make("Le Mars, IA",      42.7958, -96.1664),
			make("Champaign, IL",    40.1164, -88.2434),
			make("Indianapolis, IN", 39.7684, -86.1581)
		);
		repo.saveAll(seeds);
		System.out.println("[FORECAST CHANGE] seeded " + seeds.size() + " default locations");
	}

	private ForecastLocation make(String name, double lat, double lon) {
		ForecastLocation l = new ForecastLocation();
		l.setName(name);
		l.setLat(lat);
		l.setLon(lon);
		return l;
	}

	public List<ForecastLocation> list() {
		return repo.findAllByOrderByNameAsc();
	}

	public ForecastLocation create(ForecastLocation incoming) {
		incoming.setId(null);
		incoming.setCurrentSnapshotJson(null);
		incoming.setPreviousSnapshotJson(null);
		ForecastLocation saved = repo.save(incoming);
		// Take the first snapshot inline so the user sees data immediately on the
		// new card (otherwise they'd wait for the next 7am/7pm scheduled run).
		try {
			return refresh(saved.getId());
		} catch (Exception e) {
			System.err.println("[FORECAST CHANGE] initial refresh of new location '"
				+ saved.getName() + "' failed: " + e.getMessage());
			return saved;
		}
	}

	public ForecastLocation update(Long id, ForecastLocation incoming) {
		ForecastLocation f = repo.findById(id).orElseThrow();
		f.setName(incoming.getName());
		// If coords changed, we must re-resolve the NWS grid on next refresh
		if (!equalDouble(f.getLat(), incoming.getLat()) || !equalDouble(f.getLon(), incoming.getLon())) {
			f.setLat(incoming.getLat());
			f.setLon(incoming.getLon());
			f.setGridId(null); f.setGridX(null); f.setGridY(null);
		}
		return repo.save(f);
	}

	public void delete(Long id) {
		repo.deleteById(id);
	}

	/**
	 * Scheduled twice-daily refresh of every tracked location.
	 *
	 * Why 3:01 AM + 3:01 PM Central:
	 *   - Each NWS Weather Forecast Office issues its updated zone / gridded
	 *     forecast on a ~12-hour cadence — the major morning push is typically
	 *     published by 3 AM local time and the afternoon push by 3 PM local time.
	 *     For our Midwest tracked locations (all Central), those issuances are
	 *     already in the gridded feed by 3 AM/PM CT.
	 *   - Firing one minute after gives the publisher a small buffer without
	 *     waiting hours past issuance — the snapshot is as fresh as possible.
	 *   - The "previous" snapshot is always at most ~12 hours old, so each
	 *     card's color shading means "what changed in the latest issuance" —
	 *     useful regardless of when the user opens the page.
	 *
	 * Sequential to stay polite to NWS (one HTTP call per location).
	 */
	@Scheduled(cron = "0 1 3,15 * * *", zone = "America/Chicago")
	public void scheduledRefreshAll() {
		System.out.println("[FORECAST CHANGE] scheduled refresh starting");
		int ok = 0, fail = 0;
		for (ForecastLocation l : repo.findAll()) {
			try { refresh(l.getId()); ok++; }
			catch (Exception e) {
				fail++;
				System.err.println("[FORECAST CHANGE] refresh of '" + l.getName()
					+ "' failed: " + e.getMessage());
			}
		}
		System.out.println("[FORECAST CHANGE] scheduled refresh complete: "
			+ ok + " ok, " + fail + " failed");
	}

	/**
	 * On app startup, take an initial snapshot for any location that has never
	 * had one (e.g. the five seeded defaults on a fresh install). Locations with
	 * existing snapshots are left alone — the next 7am/7pm run will rotate them.
	 */
	@EventListener(ApplicationReadyEvent.class)
	public void initialRefreshOnStartup() {
		List<ForecastLocation> needsRefresh = repo.findAll().stream()
			.filter(l -> l.getCurrentFetchedAt() == null)
			.toList();
		if (needsRefresh.isEmpty()) return;
		System.out.println("[FORECAST CHANGE] taking initial snapshots for "
			+ needsRefresh.size() + " new location(s)");
		for (ForecastLocation l : needsRefresh) {
			try { refresh(l.getId()); }
			catch (Exception e) {
				System.err.println("[FORECAST CHANGE] initial snapshot of '"
					+ l.getName() + "' failed: " + e.getMessage());
			}
		}
	}

	/**
	 * Pull a fresh forecast for the given location.
	 * Rotates the existing `current` snapshot into `previous` first.
	 */
	public ForecastLocation refresh(Long id) {
		ForecastLocation f = repo.findById(id).orElseThrow();

		// Resolve grid if we don't have it
		if (f.getGridId() == null || f.getGridX() == null || f.getGridY() == null) {
			Map<String, Object> grid = lookupGrid(f.getLat(), f.getLon());
			if (grid != null) {
				f.setGridId((String) grid.get("gridId"));
				f.setGridX(((Number) grid.get("gridX")).intValue());
				f.setGridY(((Number) grid.get("gridY")).intValue());
			} else {
				throw new RuntimeException("Could not resolve NWS grid for " + f.getName());
			}
		}

		List<Map<String, Object>> days = fetchAndPair(f.getGridId(), f.getGridX(), f.getGridY());
		Map<String, Object> snapshot = Map.of("days", days);
		String json;
		try {
			json = mapper.writeValueAsString(snapshot);
		} catch (JsonProcessingException e) {
			throw new RuntimeException("Failed to serialize snapshot", e);
		}

		// Rotate: previous ← current; current ← new
		f.setPreviousSnapshotJson(f.getCurrentSnapshotJson());
		f.setPreviousFetchedAt(f.getCurrentFetchedAt());
		f.setCurrentSnapshotJson(json);
		f.setCurrentFetchedAt(LocalDateTime.now());

		return repo.save(f);
	}

	/** Hit NWS /points to convert lat/lon → grid identifiers. */
	@SuppressWarnings("unchecked")
	private Map<String, Object> lookupGrid(double lat, double lon) {
		try {
			URI uri = URI.create(String.format(
				"https://api.weather.gov/points/%.4f,%.4f", lat, lon));
			HttpHeaders headers = new HttpHeaders();
			headers.add("User-Agent", UA);
			headers.add("Accept", "application/geo+json");
			ResponseEntity<Map> resp = restTemplate.exchange(
				uri, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
			Map<String, Object> body = resp.getBody();
			if (body == null) return null;
			Map<String, Object> properties = (Map<String, Object>) body.get("properties");
			if (properties == null) return null;
			Map<String, Object> out = new LinkedHashMap<>();
			out.put("gridId", properties.get("gridId"));
			out.put("gridX",  properties.get("gridX"));
			out.put("gridY",  properties.get("gridY"));
			return out;
		} catch (Exception e) {
			System.err.println("[FORECAST CHANGE] points lookup failed: " + e.getMessage());
			return null;
		}
	}

	/** Fetch NWS daily forecast and pair day+night periods into one row per day. */
	@SuppressWarnings("unchecked")
	private List<Map<String, Object>> fetchAndPair(String gridId, int gridX, int gridY) {
		URI uri = URI.create(String.format(
			"https://api.weather.gov/gridpoints/%s/%d,%d/forecast", gridId, gridX, gridY));
		HttpHeaders headers = new HttpHeaders();
		headers.add("User-Agent", UA);
		headers.add("Accept", "application/geo+json");

		ResponseEntity<Map> resp = restTemplate.exchange(
			uri, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
		Map<String, Object> body = resp.getBody();
		if (body == null) return new ArrayList<>();

		Map<String, Object> properties = (Map<String, Object>) body.get("properties");
		if (properties == null) return new ArrayList<>();
		List<Map<String, Object>> periods = (List<Map<String, Object>>) properties.get("periods");
		if (periods == null) return new ArrayList<>();

		// Pair into one entry per day
		LinkedHashMap<String, Map<String, Object>> byDay = new LinkedHashMap<>();
		for (Map<String, Object> p : periods) {
			String name = (String) p.get("name");
			Boolean isDaytime = (Boolean) p.get("isDaytime");
			Number temperature = (Number) p.get("temperature");
			Map<String, Object> precipObj = (Map<String, Object>) p.get("probabilityOfPrecipitation");
			Number precipChance = precipObj == null ? null : (Number) precipObj.get("value");

			String key = dayKey(name);
			Map<String, Object> row = byDay.computeIfAbsent(key, k -> {
				Map<String, Object> r = new LinkedHashMap<>();
				r.put("day", k);
				return r;
			});

			boolean night = Boolean.FALSE.equals(isDaytime) || isNight(name);
			if (night) {
				if (temperature != null) row.put("low", temperature.intValue());
				if (precipChance != null) row.merge("precipChance", precipChance.intValue(),
					(a, b) -> Math.max(((Number) a).intValue(), ((Number) b).intValue()));
				row.putIfAbsent("nightForecast", p.get("shortForecast"));
			} else {
				if (temperature != null) row.put("high", temperature.intValue());
				if (precipChance != null) row.merge("precipChance", precipChance.intValue(),
					(a, b) -> Math.max(((Number) a).intValue(), ((Number) b).intValue()));
				row.putIfAbsent("shortForecast", p.get("shortForecast"));
				row.putIfAbsent("windSpeed",     p.get("windSpeed"));
				row.putIfAbsent("windDirection", p.get("windDirection"));
			}
		}
		// NWS gridpoint forecast typically returns ~7 days of periods, but we accept up
		// to 10 here so any future expansion of the NWS feed flows through automatically.
		return byDay.values().stream().limit(10).toList();
	}

	private static String dayKey(String name) {
		if (name == null) return "";
		if (name.equalsIgnoreCase("Tonight"))     return "Today";
		if (name.equalsIgnoreCase("This Afternoon") || name.equalsIgnoreCase("This Morning"))
			return "Today";
		return name.replaceAll("(?i)\\s+Night$", "");
	}

	private static boolean isNight(String name) {
		if (name == null) return false;
		return name.equalsIgnoreCase("Tonight") || name.toLowerCase().endsWith(" night");
	}

	private static boolean equalDouble(Double a, Double b) {
		if (a == null && b == null) return true;
		if (a == null || b == null) return false;
		return Math.abs(a - b) < 0.0001;
	}
}
