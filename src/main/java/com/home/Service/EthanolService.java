package com.home.Service;

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
 * Ethanol tracking via the U.S. Energy Information Administration (EIA) API v2.
 *
 * Two weekly series (released Wednesdays):
 *   - Fuel ethanol production  PET.W_EPOOXE_YOP_NUS_MBBLD.W  (thousand barrels/day)
 *   - Fuel ethanol stocks      PET.W_EPOOXE_SAE_NUS_MBBL.W   (thousand barrels)
 *
 * For an ag audience the headline isn't the energy number — it's corn demand:
 * ~40% of the U.S. corn crop is ground for ethanol, so we also derive the
 * implied corn grind from production (≈2.8 gal ethanol per bushel, 42 gal/bbl).
 *
 * Parsed series are cached in memory and refreshed daily (the data only changes
 * weekly). Needs a free EIA key in EIA_API_KEY.
 */
@Service
public class EthanolService {

	private static final String BASE = "https://api.eia.gov/v2/seriesid/";
	private static final String PRODUCTION_SERIES = "PET.W_EPOOXE_YOP_NUS_MBBLD.W";
	private static final String STOCKS_SERIES     = "PET.W_EPOOXE_SAE_NUS_MBBL.W";
	private static final int WEEKS = 52;

	// Corn → ethanol conversion (industry rules of thumb).
	private static final double GAL_PER_BUSHEL = 2.8;
	private static final double GAL_PER_BARREL = 42.0;

	private final RestTemplate restTemplate;

	@Value("${EIA_API_KEY:}")
	private String apiKey;

	private volatile List<Point> production = List.of();  // oldest → newest, MBBL/D
	private volatile List<Point> stocks     = List.of();  // oldest → newest, MBBL
	private volatile LocalDateTime updatedAt;

	public EthanolService(RestTemplate restTemplate) {
		this.restTemplate = restTemplate;
	}

	/** One weekly observation. */
	private record Point(String period, double value) {}

	/* ── scheduling ─────────────────────────────────────────────────────── */

	@EventListener(ApplicationReadyEvent.class)
	public void prewarm() {
		new Thread(() -> {
			try { refresh(); } catch (Exception e) {
				System.err.println("[ETHANOL] startup load failed: " + e.getMessage());
			}
		}, "ethanol-prewarm").start();
	}

	/** EIA posts weekly (Wed); a daily fetch keeps us current cheaply. */
	@Scheduled(cron = "0 0 10 * * *", zone = "America/Chicago")
	public void scheduledRefresh() {
		try { refresh(); } catch (Exception e) {
			System.err.println("[ETHANOL] scheduled load failed: " + e.getMessage());
		}
	}

	/* ── load + parse ───────────────────────────────────────────────────── */

	public void refresh() {
		if (apiKey == null || apiKey.isBlank()) {
			System.err.println("[ETHANOL] EIA_API_KEY not set — skipping fetch");
			return;
		}
		List<Point> prod = fetchSeries(PRODUCTION_SERIES);
		List<Point> stk  = fetchSeries(STOCKS_SERIES);
		if (prod.isEmpty() && stk.isEmpty()) {
			System.err.println("[ETHANOL] fetched 0 rows — check the API key / series ids");
			return;
		}
		production = prod;
		stocks = stk;
		updatedAt = LocalDateTime.now();
		System.out.println("[ETHANOL] cached " + prod.size() + " production + " + stk.size()
			+ " stocks weeks (latest " + (prod.isEmpty() ? "?" : prod.get(prod.size() - 1).period()) + ")");
	}

	@SuppressWarnings("unchecked")
	private List<Point> fetchSeries(String seriesId) {
		try {
			String url = BASE + seriesId + "?api_key=" + apiKey + "&length=" + (WEEKS + 8);
			HttpHeaders h = new HttpHeaders();
			h.set("User-Agent", "just4ag/1.0 (ethanol tracker)");
			ResponseEntity<Map> resp = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(h), Map.class);
			if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
				System.err.println("[ETHANOL] " + seriesId + " -> HTTP " + resp.getStatusCode());
				return List.of();
			}
			Map<String, Object> response = (Map<String, Object>) resp.getBody().get("response");
			List<Map<String, Object>> data = response == null ? null : (List<Map<String, Object>>) response.get("data");
			if (data == null) return List.of();

			List<Point> out = new ArrayList<>();
			for (Map<String, Object> row : data) {
				String period = row.get("period") == null ? null : row.get("period").toString();
				Double value = num(row.get("value"));
				if (period != null && value != null) out.add(new Point(period, value));
			}
			// EIA returns newest-first; flip to chronological for charting.
			java.util.Collections.reverse(out);
			return out;
		} catch (Exception e) {
			System.err.println("[ETHANOL] fetch " + seriesId + " failed: "
				+ e.getClass().getSimpleName() + " " + e.getMessage());
			return List.of();
		}
	}

	/* ── read for the API ───────────────────────────────────────────────── */

	public Map<String, Object> getEthanol() {
		Map<String, Object> out = new LinkedHashMap<>();
		out.put("source", "U.S. Energy Information Administration (EIA)");
		out.put("updatedAt", updatedAt == null ? null : updatedAt.toString());

		if (production.isEmpty() && stocks.isEmpty()) {
			out.put("message", "Ethanol data isn't loaded yet.");
			out.put("production", List.of());
			out.put("stocks", List.of());
			return out;
		}

		out.put("productionUnit", "MBBL/D");
		out.put("stocksUnit", "MBBL");
		out.put("production", tail(production));
		out.put("stocks", tail(stocks));

		Double prodLatest = last(production);
		Double prodPrev   = prev(production);
		out.put("productionLatest", prodLatest);
		out.put("productionWoW", (prodLatest != null && prodPrev != null) ? round(prodLatest - prodPrev, 1) : null);
		out.put("productionAsOf", production.isEmpty() ? null : production.get(production.size() - 1).period());
		out.put("stocksLatest", last(stocks));
		out.put("stocksAsOf", stocks.isEmpty() ? null : stocks.get(stocks.size() - 1).period());

		// Implied corn grind from the latest weekly production rate.
		if (prodLatest != null) {
			double galPerWeek = prodLatest * 1000.0 * GAL_PER_BARREL * 7.0;   // thousand bbl/d → gal/week
			double buPerWeek = galPerWeek / GAL_PER_BUSHEL;
			out.put("impliedCornBuPerWeek", Math.round(buPerWeek));
			out.put("impliedCornBuPerYear", Math.round(buPerWeek * 52.0));
		}
		return out;
	}

	private static List<Map<String, Object>> tail(List<Point> pts) {
		List<Map<String, Object>> out = new ArrayList<>();
		int from = Math.max(0, pts.size() - WEEKS);
		for (Point p : pts.subList(from, pts.size())) {
			Map<String, Object> m = new LinkedHashMap<>();
			m.put("period", p.period());
			m.put("value", p.value());
			out.add(m);
		}
		return out;
	}

	private static Double last(List<Point> p) { return p.isEmpty() ? null : p.get(p.size() - 1).value(); }
	private static Double prev(List<Point> p) { return p.size() < 2 ? null : p.get(p.size() - 2).value(); }

	private static Double num(Object o) {
		if (o == null) return null;
		if (o instanceof Number) return ((Number) o).doubleValue();
		try { return Double.parseDouble(o.toString()); } catch (Exception e) { return null; }
	}
	private static double round(double v, int places) {
		double f = Math.pow(10, places);
		return Math.round(v * f) / f;
	}
}
