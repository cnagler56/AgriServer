package com.home.Service;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * CFTC Commitments of Traders — weekly managed-money ("the funds") net futures
 * positioning for corn, soybeans, and Chicago SRW wheat. Public Socrata API, no
 * key. Released Fridays (data as of the prior Tuesday). Cached in memory and
 * refreshed daily; consumed by the news feed.
 */
@Service
public class CotService {

	private static final String BASE = "https://publicreporting.cftc.gov/resource/72hh-3qpy.json";
	private static final Map<String, String> MARKETS = Map.of(
		"CORN",         "CORN - CHICAGO BOARD OF TRADE",
		"SOYBEANS",     "SOYBEANS - CHICAGO BOARD OF TRADE",
		"WHEAT",        "WHEAT-SRW - CHICAGO BOARD OF TRADE",
		"SOYBEAN_MEAL", "SOYBEAN MEAL - CHICAGO BOARD OF TRADE",
		"SOYBEAN_OIL",  "SOYBEAN OIL - CHICAGO BOARD OF TRADE",
		"LIVE_CATTLE",  "LIVE CATTLE - CHICAGO MERCANTILE EXCHANGE",
		"LEAN_HOGS",    "LEAN HOGS - CHICAGO MERCANTILE EXCHANGE",
		"COTTON",       "COTTON NO. 2 - ICE FUTURES U.S.");

	private final RestTemplate restTemplate;

	private volatile String reportDate;                  // "2026-06-09"
	private volatile Map<String, Pos> positions = Map.of();
	private volatile LocalDateTime updatedAt;

	public CotService(RestTemplate restTemplate) {
		this.restTemplate = restTemplate;
	}

	/** Managed-money positioning (contracts): longs, shorts, net, and weekly net change. */
	public record Pos(long longs, long shorts, long net, long netChange) {}

	public String getReportDate() { return reportDate; }
	public Pos getPosition(String commodity) { return positions.get(commodity); }
	public LocalDateTime getUpdatedAt() { return updatedAt; }

	/* ── scheduling ─────────────────────────────────────────────────────── */

	@EventListener(ApplicationReadyEvent.class)
	public void prewarm() {
		new Thread(() -> {
			try { refresh(); } catch (Exception e) {
				System.err.println("[COT] startup load failed: " + e.getMessage());
			}
		}, "cot-prewarm").start();
	}

	/** Released Fridays; a daily fetch keeps us current cheaply. */
	@Scheduled(cron = "0 15 7 * * *", zone = "America/Chicago")
	public void scheduledRefresh() {
		try { refresh(); } catch (Exception e) {
			System.err.println("[COT] scheduled load failed: " + e.getMessage());
		}
	}

	/* ── load + parse ───────────────────────────────────────────────────── */

	public void refresh() {
		Map<String, Pos> out = new HashMap<>();
		String latestDate = null;
		for (Map.Entry<String, String> e : MARKETS.entrySet()) {
			Map<String, Object> row = fetchLatest(e.getValue());
			if (row == null) continue;
			Long lng = lng(row.get("m_money_positions_long_all"));
			Long sht = lng(row.get("m_money_positions_short_all"));
			Long dL  = lng(row.get("change_in_m_money_long_all"));
			Long dS  = lng(row.get("change_in_m_money_short_all"));
			if (lng == null || sht == null) continue;
			long net = lng - sht;
			long netChg = (dL != null && dS != null) ? dL - dS : 0;
			out.put(e.getKey(), new Pos(lng, sht, net, netChg));
			String d = str(row.get("report_date_as_yyyy_mm_dd"));
			if (d != null && d.length() >= 10) latestDate = d.substring(0, 10);
		}
		if (out.isEmpty()) { System.err.println("[COT] fetched 0 markets"); return; }
		positions = out;
		reportDate = latestDate;
		updatedAt = LocalDateTime.now();
		System.out.println("[COT] cached managed-money positions for " + latestDate + " (" + out.size() + " markets)");
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> fetchLatest(String marketName) {
		try {
			String url = BASE + "?market_and_exchange_names=" + marketName.replace(" ", "%20")
				+ "&$order=report_date_as_yyyy_mm_dd%20DESC&$limit=1";
			Map<String, Object>[] arr = restTemplate.getForObject(URI.create(url), Map[].class);
			return (arr == null || arr.length == 0) ? null : arr[0];
		} catch (Exception e) {
			System.err.println("[COT] fetch '" + marketName + "' failed: " + e.getMessage());
			return null;
		}
	}

	private static Long lng(Object o) {
		if (o == null) return null;
		if (o instanceof Number) return ((Number) o).longValue();
		try { return (long) Double.parseDouble(o.toString()); } catch (Exception e) { return null; }
	}
	private static String str(Object o) { return o == null ? null : o.toString(); }
}
