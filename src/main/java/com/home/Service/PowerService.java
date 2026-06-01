package com.home.Service;

import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Thin proxy to the NASA POWER API (https://power.larc.nasa.gov).
 *
 * POWER is the standard public source for free daily ag/met data — soil moisture,
 * temperature, solar, precip — anywhere on Earth, no auth.
 *
 * We use two endpoints in this app:
 *   - GWETTOP / GWETROOT / GWETPROF  — soil-moisture fractions for the weather page
 *   - T2M_MAX / T2M_MIN              — growing-degree-day accumulation for fields
 *
 * Responses cached for 6 hours per (lat, lon, start, end, params) key — POWER data
 * is daily-resolution and is rate-limited if hammered.
 */
@Service
public class PowerService {

	private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");
	private static final long CACHE_TTL_SECONDS = 6 * 60 * 60;
	private static final String POWER_URL = "https://power.larc.nasa.gov/api/temporal/daily/point";

	private final RestTemplate restTemplate;
	private final ConcurrentHashMap<String, CachedResponse> cache = new ConcurrentHashMap<>();

	public PowerService(RestTemplate restTemplate) {
		this.restTemplate = restTemplate;
	}

	/**
	 * Fetch a series for one or more daily parameters. Returns one row per day
	 * with the requested values keyed by parameter name (e.g. "GWETROOT").
	 *
	 * Dates are inclusive; the POWER API uses yyyyMMdd format.
	 */
	@SuppressWarnings("unchecked")
	public List<Map<String, Object>> fetchDaily(double lat, double lon,
			LocalDate start, LocalDate end, String parameters) {
		String key = lat + "|" + lon + "|" + start + "|" + end + "|" + parameters;
		CachedResponse hit = cache.get(key);
		long now = System.currentTimeMillis() / 1000;
		if (hit != null && now - hit.fetchedAt < CACHE_TTL_SECONDS) {
			return hit.rows;
		}

		URI uri = UriComponentsBuilder.fromHttpUrl(POWER_URL)
				.queryParam("parameters", parameters)
				.queryParam("community", "ag")
				.queryParam("longitude", lon)
				.queryParam("latitude", lat)
				.queryParam("start", start.format(YYYYMMDD))
				.queryParam("end",   end.format(YYYYMMDD))
				.queryParam("format", "JSON")
				.build()
				.encode()
				.toUri();

		System.out.println("[POWER] " + uri);

		List<Map<String, Object>> rows = new ArrayList<>();
		try {
			Map<String, Object> body = restTemplate.getForObject(uri, Map.class);
			if (body == null) return rows;

			Map<String, Object> properties = (Map<String, Object>) body.get("properties");
			Map<String, Object> parameter  = properties == null ? null : (Map<String, Object>) properties.get("parameter");
			if (parameter == null) return rows;

			// POWER returns one map per parameter, keyed by yyyyMMdd → value.
			// We pivot into one row per date with all requested params.
			String[] requested = parameters.split(",");
			Map<String, Map<String, Object>> perParam = new HashMap<>();
			for (String p : requested) {
				Object obj = parameter.get(p.trim());
				if (obj instanceof Map) perParam.put(p.trim(), (Map<String, Object>) obj);
			}

			// Use the first parameter's date keys as the canonical date list
			Map<String, Object> firstMap = perParam.values().stream().findFirst().orElse(null);
			if (firstMap == null) return rows;

			List<String> dates = new ArrayList<>(firstMap.keySet());
			java.util.Collections.sort(dates);
			for (String d : dates) {
				Map<String, Object> row = new HashMap<>();
				row.put("date", d);
				for (String p : requested) {
					Map<String, Object> series = perParam.get(p.trim());
					Object raw = series == null ? null : series.get(d);
					Double v = num(raw);
					// POWER returns -999 to mean "no data"
					row.put(p.trim(), (v != null && v <= -900) ? null : v);
				}
				rows.add(row);
			}
		} catch (Exception e) {
			System.err.println("[POWER] error: " + e.getClass().getSimpleName() + " - " + e.getMessage());
		}

		cache.put(key, new CachedResponse(rows, now));
		return rows;
	}

	private static Double num(Object o) {
		if (o == null) return null;
		if (o instanceof Number) return ((Number) o).doubleValue();
		try { return Double.parseDouble(o.toString()); } catch (Exception e) { return null; }
	}

	private static class CachedResponse {
		final List<Map<String, Object>> rows;
		final long fetchedAt;
		CachedResponse(List<Map<String, Object>> rows, long fetchedAt) {
			this.rows = rows; this.fetchedAt = fetchedAt;
		}
	}
}
