package com.home.Service;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.home.Domain.CommodityGroup;
import com.home.Domain.CommodityPrice;

/**
 * Fetches commodity-futures prices from Yahoo Finance.
 *
 * Returns the next 5 unexpired contracts per commodity (front month + 4 deferreds).
 * Each contract symbol is generated on demand using the standard CME/CBOT format:
 *
 *     <root><month-code><yy>.<exchange>     e.g. ZCN26.CBT  =  Corn, July 2026
 *
 * Active months per commodity follow exchange-published rules:
 *   Corn / Wheat:   H K N U Z       (Mar, May, Jul, Sep, Dec)
 *   Soybeans:       F H K N Q U X   (Jan, Mar, May, Jul, Aug, Sep, Nov)
 *   Live Cattle:    G J M Q V Z     (Feb, Apr, Jun, Aug, Oct, Dec)
 *   Lean Hogs:      G J K M N Q V Z (Feb, Apr, May, Jun, Jul, Aug, Oct, Dec)
 *
 * In-memory cache with a 5-minute TTL keyed by Yahoo symbol — the cache survives
 * across calls so the contract list is essentially free after the first hit.
 */
@Service
public class PriceService {

	private static final long CACHE_TTL_SECONDS = 300;            // 5 minutes
	private static final int CONTRACTS_PER_COMMODITY = 5;
	/** Extra candidates we try beyond the 5 we keep, to cover expired/gap contracts. */
	private static final int CONTRACT_BUFFER = 4;
	private static final String YAHOO_URL = "https://query1.finance.yahoo.com/v8/finance/chart/";

	/**
	 * Static config per commodity.
	 *
	 * `expiresByDay` is the day-of-the-contract-month at which we roll forward to the
	 * next contract. We set this to 1 across the board: liquidity in the spot-month
	 * contract dries up well before formal exchange expiry (front-month volume migrates
	 * to the next deferred contract as traders avoid delivery / first notice). Rolling
	 * on the 1st of the contract month gives users the actively-traded price all month,
	 * not a stale near-zero-volume number from a dying contract.
	 *
	 * If you ever want a per-commodity rule (e.g. live cattle holds spot month longer
	 * than grains), this field is the single knob to change.
	 */
	private static final CommodityConfig[] CATALOG = new CommodityConfig[] {
		new CommodityConfig("ZC", "Corn",        "¢/bu", "CBT", new int[] {3, 5, 7, 9, 12},                1),
		new CommodityConfig("ZS", "Soybeans",    "¢/bu", "CBT", new int[] {1, 3, 5, 7, 8, 9, 11},          1),
		new CommodityConfig("ZW", "Wheat",       "¢/bu", "CBT", new int[] {3, 5, 7, 9, 12},                1),
		new CommodityConfig("LE", "Live Cattle", "¢/lb", "CME", new int[] {2, 4, 6, 8, 10, 12},            1),
		new CommodityConfig("HE", "Lean Hogs",   "¢/lb", "CME", new int[] {2, 4, 5, 6, 7, 8, 10, 12},      1),
	};

	/** Yahoo / CME month codes — index 0 = January. */
	private static final char[] MONTH_CODES = {
		'F', 'G', 'H', 'J', 'K', 'M', 'N', 'Q', 'U', 'V', 'X', 'Z'
	};

	private final RestTemplate restTemplate;
	private final ConcurrentHashMap<String, CachedQuote> cache = new ConcurrentHashMap<>();

	public PriceService(RestTemplate restTemplate) {
		this.restTemplate = restTemplate;
	}

	/**
	 * Returns each commodity with its next 5 unexpired, price-available contracts (front month first).
	 *
	 * The pipeline is:
	 *   1. Generate {@code 5 + buffer} candidate contract slots that haven't expired by date
	 *   2. Fetch each from Yahoo (with caching)
	 *   3. Skip any fetch that came back without a `last` price — Yahoo occasionally has gaps on
	 *      thinly-traded out months even when our date rules say the contract should be active
	 *   4. Stop once we've collected 5 good contracts
	 */
	public List<CommodityGroup> getPricesGrouped() {
		List<CommodityGroup> result = new ArrayList<>();
		int candidateCount = CONTRACTS_PER_COMMODITY + CONTRACT_BUFFER;
		for (CommodityConfig cfg : CATALOG) {
			CommodityGroup group = new CommodityGroup(cfg.name, cfg.unit, cfg.exchange);
			for (Contract c : nextContracts(cfg, candidateCount)) {
				if (group.getContracts().size() >= CONTRACTS_PER_COMMODITY) break;
				CommodityPrice p = getOrFetch(cfg, c);
				// Skip contracts where Yahoo returned no usable price (expired, gap, etc.)
				if (p.getLast() == null) continue;
				group.getContracts().add(p);
			}
			result.add(group);
		}
		return result;
	}

	/**
	 * Generate the next {@code n} (year, month) contract slots whose last trading day
	 * is still in the future. Uses each commodity's own expiry-day rule rather than a
	 * blanket "month < current month" check.
	 */
	private static List<Contract> nextContracts(CommodityConfig cfg, int n) {
		LocalDate today = LocalDate.now();
		List<Contract> out = new ArrayList<>(n);
		int year = today.getYear();
		int safetyYearsAhead = 0;
		while (out.size() < n && safetyYearsAhead < 6) {
			for (int m : cfg.activeMonths) {
				if (!isStillTrading(cfg, year, m, today)) continue;
				out.add(new Contract(year, m));
				if (out.size() >= n) break;
			}
			year++;
			safetyYearsAhead++;
		}
		return out;
	}

	/**
	 * Is the contract for ({@code contractYear}, {@code contractMonth}) of {@code cfg}
	 * still trading as of {@code today}?
	 *
	 *   - Past calendar years → no
	 *   - Future calendar years → yes
	 *   - This year, earlier month → no
	 *   - This year, later month → yes
	 *   - This year, same month → yes iff today.day < cfg.expiresByDay
	 */
	private static boolean isStillTrading(CommodityConfig cfg, int contractYear, int contractMonth, LocalDate today) {
		if (contractYear < today.getYear()) return false;
		if (contractYear > today.getYear()) return true;
		if (contractMonth < today.getMonthValue()) return false;
		if (contractMonth > today.getMonthValue()) return true;
		return today.getDayOfMonth() < cfg.expiresByDay;
	}

	private CommodityPrice getOrFetch(CommodityConfig cfg, Contract c) {
		String symbol = buildSymbol(cfg, c);
		CachedQuote hit = cache.get(symbol);
		long now = Instant.now().getEpochSecond();
		if (hit != null && (now - hit.fetchedAt) < CACHE_TTL_SECONDS) {
			return hit.price;
		}
		CommodityPrice fresh = fetchFromYahoo(cfg, c, symbol);
		cache.put(symbol, new CachedQuote(fresh, now));
		return fresh;
	}

	private static String buildSymbol(CommodityConfig cfg, Contract c) {
		// e.g. "ZC" + "N" + "26" + ".CBT" → ZCN26.CBT
		return cfg.root
			+ MONTH_CODES[c.month - 1]
			+ String.format("%02d", c.year % 100)
			+ "." + cfg.exchange;
	}

	@SuppressWarnings("unchecked")
	private CommodityPrice fetchFromYahoo(CommodityConfig cfg, Contract c, String symbol) {
		CommodityPrice p = new CommodityPrice(symbol, cfg.name, cfg.unit);
		// Nice display label e.g. "Jul 2026"
		String label = Month.of(c.month).name();
		label = label.charAt(0) + label.substring(1, 3).toLowerCase() + " " + c.year;
		p.setExpiration(label);
		p.setExpirationKey(c.year * 100 + c.month);

		try {
			URI uri = URI.create(YAHOO_URL + symbol);

			HttpHeaders headers = new HttpHeaders();
			headers.add("User-Agent",
				"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
				+ "Chrome/120.0.0.0 Safari/537.36");
			headers.add("Accept", "application/json");

			ResponseEntity<Map> resp = restTemplate.exchange(
				uri, HttpMethod.GET, new HttpEntity<>(headers), Map.class);

			Map<String, Object> body = resp.getBody();
			Map<String, Object> chart = body == null ? null : (Map<String, Object>) body.get("chart");
			List<Map<String, Object>> result = chart == null ? null : (List<Map<String, Object>>) chart.get("result");
			Map<String, Object> first = (result == null || result.isEmpty()) ? null : result.get(0);
			Map<String, Object> meta  = first == null ? null : (Map<String, Object>) first.get("meta");

			if (meta != null) {
				Double last = num(meta.get("regularMarketPrice"));
				Double prev = num(meta.get("chartPreviousClose"));
				if (prev == null) prev = num(meta.get("previousClose"));
				Long ts = longOrNull(meta.get("regularMarketTime"));
				p.setLast(last);
				p.setPreviousClose(prev);
				if (last != null && prev != null && prev != 0.0) {
					double change = last - prev;
					p.setChange(round(change, 2));
					p.setChangePercent(round((change / prev) * 100.0, 2));
				}
				p.setAsOf(ts);
			} else {
				p.setError("no data in response");
			}
		} catch (Exception e) {
			System.err.println("[PRICES] " + symbol + " failed: "
				+ e.getClass().getSimpleName() + " - " + e.getMessage());
			p.setError(e.getClass().getSimpleName());
		}
		return p;
	}

	private static Double num(Object o) {
		if (o == null) return null;
		if (o instanceof Number) return ((Number) o).doubleValue();
		try { return Double.parseDouble(o.toString()); } catch (Exception e) { return null; }
	}

	private static Long longOrNull(Object o) {
		if (o == null) return null;
		if (o instanceof Number) return ((Number) o).longValue();
		try { return Long.parseLong(o.toString()); } catch (Exception e) { return null; }
	}

	private static double round(double v, int places) {
		double f = Math.pow(10, places);
		return Math.round(v * f) / f;
	}

	private static class CommodityConfig {
		final String root;
		final String name;
		final String unit;
		final String exchange;
		final int[] activeMonths;
		/** Day-of-contract-month at which the contract is considered no longer trading. */
		final int expiresByDay;
		CommodityConfig(String root, String name, String unit, String exchange,
				int[] activeMonths, int expiresByDay) {
			this.root = root; this.name = name; this.unit = unit;
			this.exchange = exchange; this.activeMonths = activeMonths;
			this.expiresByDay = expiresByDay;
		}
	}

	private static class Contract {
		final int year;
		final int month;
		Contract(int year, int month) { this.year = year; this.month = month; }
	}

	private static class CachedQuote {
		final CommodityPrice price;
		final long fetchedAt;
		CachedQuote(CommodityPrice price, long fetchedAt) {
			this.price = price;
			this.fetchedAt = fetchedAt;
		}
	}
}
