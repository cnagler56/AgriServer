package com.home.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * El Niño / La Niña (ENSO) tracking via NOAA CPC's Oceanic Niño Index (ONI) —
 * the official measure used to classify El Niño (warm) / La Niña (cool) / Neutral.
 *
 * Source is a plain whitespace-delimited table:
 *   https://www.cpc.ncep.noaa.gov/data/indices/oni.ascii.txt
 *     SEAS  YR   TOTAL   ANOM
 *     DJF  1950  24.72  -1.53
 *     ...
 * The ANOM column is the ONI value (°C anomaly). We cache the parsed series in
 * memory and refresh daily — the table only changes once a month.
 */
@Service
public class EnsoService {

	private static final String ONI_URL = "https://www.cpc.ncep.noaa.gov/data/indices/oni.ascii.txt";
	private static final int HISTORY_SEASONS = 72; // ~6 years of 3-month seasons for the chart

	private final RestTemplate restTemplate;

	/** Optional local file fallback (a saved oni.ascii.txt) if the host blocks outbound fetches. */
	@Value("${ENSO_ONI_PATH:}")
	private String oniPath;

	/** Parsed series, oldest → newest. Volatile so the scheduled refresh is visible to requests. */
	private volatile List<Season> series = List.of();
	private volatile LocalDateTime updatedAt;

	public EnsoService(RestTemplate restTemplate) {
		this.restTemplate = restTemplate;
	}

	/** One 3-month ONI season, e.g. ("MAM", 2026, 0.48). */
	private record Season(String seas, int year, double oni) {}

	/* ── scheduling ─────────────────────────────────────────────────────── */

	@EventListener(ApplicationReadyEvent.class)
	public void prewarm() {
		new Thread(() -> {
			try { refresh(); } catch (Exception e) {
				System.err.println("[ENSO] startup load failed: " + e.getMessage());
			}
		}, "enso-prewarm").start();
	}

	/** ONI updates monthly; a daily fetch of a tiny file keeps us current cheaply. */
	@Scheduled(cron = "0 30 6 * * *", zone = "America/Chicago")
	public void scheduledRefresh() {
		try { refresh(); } catch (Exception e) {
			System.err.println("[ENSO] scheduled load failed: " + e.getMessage());
		}
	}

	/* ── load + parse ───────────────────────────────────────────────────── */

	public void refresh() {
		String text = readLocal();
		if (text == null) text = fetch(ONI_URL);
		if (text == null) { System.err.println("[ENSO] no ONI data available"); return; }

		List<Season> parsed = parse(text);
		if (parsed.isEmpty()) { System.err.println("[ENSO] parsed 0 ONI rows — check the table layout"); return; }
		series = parsed;
		updatedAt = LocalDateTime.now();
		System.out.println("[ENSO] cached " + parsed.size() + " ONI seasons (latest "
			+ parsed.get(parsed.size() - 1).seas() + " " + parsed.get(parsed.size() - 1).year() + ")");
	}

	private List<Season> parse(String text) {
		List<Season> out = new ArrayList<>();
		for (String line : text.split("\r?\n")) {
			String[] f = line.trim().split("\\s+");
			if (f.length < 4) continue;
			Integer year = parseInt(f[1]);
			Double oni = parseDbl(f[3]);
			if (year == null || oni == null) continue;   // skips the header row too
			out.add(new Season(f[0], year, oni));
		}
		return out;
	}

	private String readLocal() {
		if (oniPath == null || oniPath.isBlank()) return null;
		try {
			Path p = Path.of(oniPath.trim());
			if (!Files.exists(p)) return null;
			System.out.println("[ENSO] reading local file " + p);
			return new String(Files.readAllBytes(p), StandardCharsets.ISO_8859_1);
		} catch (Exception e) {
			System.err.println("[ENSO] local read failed: " + e.getMessage());
			return null;
		}
	}

	private String fetch(String url) {
		try {
			HttpHeaders h = new HttpHeaders();
			h.set("User-Agent", "just4ag/1.0 (ENSO tracker)");
			ResponseEntity<String> resp = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(h), String.class);
			if (resp.getStatusCode().is2xxSuccessful()) return resp.getBody();
			System.err.println("[ENSO] " + url + " -> HTTP " + resp.getStatusCode());
			return null;
		} catch (Exception e) {
			System.err.println("[ENSO] fetch failed: " + e.getClass().getSimpleName() + " " + e.getMessage());
			return null;
		}
	}

	/* ── read for the API ───────────────────────────────────────────────── */

	public Map<String, Object> getEnso() {
		Map<String, Object> out = new LinkedHashMap<>();
		out.put("source", "NOAA CPC — Oceanic Niño Index (ONI)");
		out.put("updatedAt", updatedAt == null ? null : updatedAt.toString());

		if (series.isEmpty()) {
			out.put("message", "ENSO data isn't loaded yet.");
			out.put("current", null);
			out.put("history", List.of());
			return out;
		}

		Season latest = series.get(series.size() - 1);
		out.put("current", describe(latest));

		List<Map<String, Object>> history = new ArrayList<>();
		int from = Math.max(0, series.size() - HISTORY_SEASONS);
		for (Season s : series.subList(from, series.size())) {
			Map<String, Object> m = new LinkedHashMap<>();
			m.put("season", s.seas());
			m.put("year", s.year());
			m.put("oni", s.oni());
			history.add(m);
		}
		out.put("history", history);
		return out;
	}

	private Map<String, Object> describe(Season s) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("season", s.seas());
		m.put("year", s.year());
		m.put("oni", s.oni());
		m.put("phase", phase(s.oni()));
		m.put("label", label(s.oni()));
		m.put("strength", strength(s.oni()));
		return m;
	}

	/** Point-in-time phase from the latest 3-month ONI value. */
	private static String phase(double oni) {
		if (oni >= 0.5) return "EL_NINO";
		if (oni <= -0.5) return "LA_NINA";
		return "NEUTRAL";
	}

	private static String label(double oni) {
		if (oni >= 0.5) return "El Niño";
		if (oni <= -0.5) return "La Niña";
		return "Neutral";
	}

	private static String strength(double oni) {
		double a = Math.abs(oni);
		if (a < 0.5) return "";
		if (a < 1.0) return "Weak";
		if (a < 1.5) return "Moderate";
		if (a < 2.0) return "Strong";
		return "Very Strong";
	}

	private static Integer parseInt(String s) {
		try { return Integer.parseInt(s.trim()); } catch (Exception e) { return null; }
	}
	private static Double parseDbl(String s) {
		try { return Double.parseDouble(s.trim()); } catch (Exception e) { return null; }
	}
}
