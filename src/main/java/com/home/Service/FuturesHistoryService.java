package com.home.Service;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.home.Domain.FuturesHistory;
import com.home.Repository.FuturesHistoryRepository;

/**
 * Weekly daily-OHLC history for each commodity's front-month futures contract,
 * from Yahoo's chart endpoint. Feeds the site's own candlestick chart (rendered
 * client-side), so we're not dependent on a third-party chart widget that can't
 * show CBOT futures.
 *
 * Refreshes Friday evening after the close (weekly is all the chart needs) plus
 * a startup prewarm, and serves the last good series if a fetch fails.
 */
@Service
public class FuturesHistoryService {

	private static final String YAHOO = "https://query1.finance.yahoo.com/v8/finance/chart/";
	private static final String UA =
		"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36";

	/** Site commodity → Yahoo front-month continuous futures symbol. */
	private static final Map<String, String> SYMBOL = Map.of(
		"CORN", "ZC=F", "SOYBEANS", "ZS=F", "SOYBEAN_MEAL", "ZM=F",
		"SOYBEAN_OIL", "ZL=F", "WHEAT", "ZW=F", "COTTON", "CT=F");

	private final RestTemplate restTemplate;
	private final FuturesHistoryRepository repo;
	private final ObjectMapper mapper = new ObjectMapper();

	public FuturesHistoryService(RestTemplate restTemplate, FuturesHistoryRepository repo) {
		this.restTemplate = restTemplate;
		this.repo = repo;
	}

	/* ── scheduling ─────────────────────────────────────────────────────── */

	@EventListener(ApplicationReadyEvent.class)
	public void prewarm() {
		Thread t = new Thread(() -> {
			try { refreshAll(); } catch (Exception e) {
				System.err.println("[FUTHIST] startup refresh failed: " + e.getMessage());
			}
		}, "futhist-prewarm");
		t.setDaemon(true);
		t.start();
	}

	/** Friday 8pm Central — after the week's grain close. */
	@Scheduled(cron = "0 0 20 * * FRI", zone = "America/Chicago")
	public void weeklyRefresh() {
		try { refreshAll(); } catch (Exception e) {
			System.err.println("[FUTHIST] weekly refresh failed: " + e.getMessage());
		}
	}

	public void refreshAll() {
		for (String commodity : SYMBOL.keySet()) {
			try { refreshOne(commodity); } catch (Exception e) {
				System.err.println("[FUTHIST] " + commodity + " failed: "
					+ e.getClass().getSimpleName() + " - " + e.getMessage());
			}
		}
	}

	/* ── fetch + store ──────────────────────────────────────────────────── */

	@Transactional
	void refreshOne(String commodity) throws Exception {
		String symbol = SYMBOL.get(commodity);
		if (symbol == null) return;

		URI uri = URI.create(YAHOO + symbol + "?range=1y&interval=1d");
		HttpHeaders h = new HttpHeaders();
		h.add("User-Agent", UA);
		h.add("Accept", "application/json");
		@SuppressWarnings("rawtypes")
		ResponseEntity<Map> resp = restTemplate.exchange(uri, HttpMethod.GET, new HttpEntity<>(h), Map.class);

		Parsed parsed = parse(resp.getBody());
		if (parsed == null || parsed.bars.isEmpty()) {
			System.err.println("[FUTHIST] no bars for " + commodity + " (" + symbol + ") — keeping last good");
			return;
		}

		FuturesHistory fh = repo.findByCommodity(commodity).orElseGet(FuturesHistory::new);
		fh.setCommodity(commodity);
		fh.setSymbol(symbol);
		fh.setCurrency(parsed.currency);
		fh.setBarsJson(mapper.writeValueAsString(parsed.bars));
		fh.setUpdatedAt(LocalDateTime.now());
		repo.save(fh);
		System.out.println("[FUTHIST] stored " + parsed.bars.size() + " bars for " + commodity);
	}

	@SuppressWarnings("unchecked")
	private Parsed parse(Map<String, Object> body) {
		Map<String, Object> chart = body == null ? null : (Map<String, Object>) body.get("chart");
		List<Map<String, Object>> result = chart == null ? null : (List<Map<String, Object>>) chart.get("result");
		Map<String, Object> first = (result == null || result.isEmpty()) ? null : result.get(0);
		if (first == null) return null;

		Map<String, Object> meta = (Map<String, Object>) first.get("meta");
		String currency = meta == null ? "USX" : String.valueOf(meta.getOrDefault("currency", "USX"));

		List<Object> ts = (List<Object>) first.get("timestamp");
		Map<String, Object> indicators = (Map<String, Object>) first.get("indicators");
		List<Map<String, Object>> quotes = indicators == null ? null
			: (List<Map<String, Object>>) indicators.get("quote");
		Map<String, Object> q = (quotes == null || quotes.isEmpty()) ? null : quotes.get(0);
		if (ts == null || q == null) return null;

		List<Object> open = (List<Object>) q.get("open");
		List<Object> high = (List<Object>) q.get("high");
		List<Object> low = (List<Object>) q.get("low");
		List<Object> close = (List<Object>) q.get("close");
		List<Object> vol = (List<Object>) q.get("volume");

		List<Map<String, Object>> bars = new ArrayList<>(ts.size());
		for (int i = 0; i < ts.size(); i++) {
			Double o = num(at(open, i)), hi = num(at(high, i)), lo = num(at(low, i)), c = num(at(close, i));
			Long t = lng(ts.get(i));
			if (t == null || o == null || hi == null || lo == null || c == null) continue;   // holiday gap
			Map<String, Object> bar = new LinkedHashMap<>();
			bar.put("t", t);
			bar.put("o", round(o));
			bar.put("h", round(hi));
			bar.put("l", round(lo));
			bar.put("c", round(c));
			Double v = num(at(vol, i));
			bar.put("v", v == null ? 0L : v.longValue());
			bars.add(bar);
		}
		Parsed p = new Parsed();
		p.currency = currency;
		p.bars = bars;
		return p;
	}

	/* ── read for the API ───────────────────────────────────────────────── */

	@SuppressWarnings("unchecked")
	public Map<String, Object> getHistory(String commodity) {
		commodity = commodity.toUpperCase();
		Map<String, Object> out = new LinkedHashMap<>();
		out.put("commodity", commodity);
		FuturesHistory fh = repo.findByCommodity(commodity).orElse(null);
		if (fh == null || fh.getBarsJson() == null) {
			out.put("bars", List.of());
			out.put("message", SYMBOL.containsKey(commodity)
				? "Price history isn't loaded yet — check back shortly."
				: "No futures chart is configured for this product.");
			return out;
		}
		out.put("symbol", fh.getSymbol());
		out.put("currency", fh.getCurrency());
		out.put("updatedAt", fh.getUpdatedAt() == null ? null : fh.getUpdatedAt().toString());
		try {
			out.put("bars", mapper.readValue(fh.getBarsJson(), List.class));
		} catch (Exception e) {
			out.put("bars", List.of());
		}
		return out;
	}

	/* ── helpers ────────────────────────────────────────────────────────── */

	private static class Parsed { String currency; List<Map<String, Object>> bars; }

	private static Object at(List<Object> l, int i) { return (l != null && i < l.size()) ? l.get(i) : null; }
	private static Double num(Object o) {
		if (o == null) return null;
		if (o instanceof Number) return ((Number) o).doubleValue();
		try { return Double.parseDouble(o.toString()); } catch (Exception e) { return null; }
	}
	private static Long lng(Object o) {
		if (o == null) return null;
		if (o instanceof Number) return ((Number) o).longValue();
		try { return Long.parseLong(o.toString()); } catch (Exception e) { return null; }
	}
	private static double round(double v) { return Math.round(v * 100.0) / 100.0; }
}
