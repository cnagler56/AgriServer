package com.home.Service;

import java.net.URI;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.home.Domain.PlantingSnapshot;
import com.home.Domain.YieldSnapshot;
import com.home.Repository.PlantingSnapshotRepository;
import com.home.Repository.YieldSnapshotRepository;
import com.home.Service.ApiResponse.ApiItem;

/**
 * Backs the unified /usda-reports page.
 *
 *   - getYieldData(commodity)    — NASS Crop Production yield estimates (in-season + final)
 *   - getPlantingData(commodity) — NASS Prospective Plantings / Acreage report
 *
 * Both report types follow the same caching pattern: fetch from NASS once, persist in
 * the DB, and auto-refresh after REFRESH_DAYS so we pick up the next monthly report
 * within a week of publication without manual action.
 */
@Service
public class UsdaReportsService {

	private static final long REFRESH_DAYS = 7;
	private static final String NASS_URL = "https://quickstats.nass.usda.gov/api/api_GET/";
	private static final DateTimeFormatter NASS_LOAD_FMT =
		DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss[.SSS]");

	/** Sortable ordinal for NASS yield reference periods. Higher = more recent / more final. */
	private static final Map<String, Integer> REF_PERIOD_RANK = Map.ofEntries(
		Map.entry("YEAR", 100),
		Map.entry("DEC",   90),
		Map.entry("NOV",   80),
		Map.entry("OCT",   70),
		Map.entry("SEP",   60),
		Map.entry("AUG",   50),
		Map.entry("JUL",   40),
		Map.entry("JUN",   30),
		Map.entry("MAY",   20)
	);

	@Value("${usda.api.key}")
	private String apiKey;

	private final RestTemplate restTemplate;
	private final YieldSnapshotRepository yieldRepo;
	private final PlantingSnapshotRepository plantingRepo;

	public UsdaReportsService(RestTemplate restTemplate,
			YieldSnapshotRepository yieldRepo,
			PlantingSnapshotRepository plantingRepo) {
		this.restTemplate = restTemplate;
		this.yieldRepo = yieldRepo;
		this.plantingRepo = plantingRepo;
	}

	// ── YIELD ESTIMATOR ───────────────────────────────────────────────────────

	public Map<String, Object> getYieldData(String commodity) {
		commodity = commodity.toUpperCase();
		int currentYear = Year.now().getValue();
		int priorYear   = currentYear - 1;

		refreshYieldIfStale(commodity, currentYear, priorYear);

		List<YieldSnapshot> currentYearRows = yieldRepo.findByCommodityAndYear(commodity, currentYear);
		List<YieldSnapshot> priorYearRows   = yieldRepo.findByCommodityAndYear(commodity, priorYear);

		boolean fellBack = false;
		if (currentYearRows.isEmpty()) {
			currentYearRows = priorYearRows;
			fellBack = true;
		}

		Map<String, YieldSnapshot> currentLatest = pickLatestPerState(currentYearRows);
		Map<String, YieldSnapshot> priorLatest   = pickLatestPerState(priorYearRows);

		String asOfLabel = currentLatest.values().stream()
			.map(YieldSnapshot::getReferencePeriod)
			.filter(p -> p != null)
			.max(Comparator.comparingInt(p -> REF_PERIOD_RANK.getOrDefault(p, 0)))
			.orElse("—");

		Map<String, Object> out = new HashMap<>();
		out.put("commodity",        commodity);
		out.put("currentYear",      fellBack ? priorYear : currentYear);
		out.put("priorYear",        priorYear);
		out.put("currentAsOf",      asOfLabel);
		out.put("fellBack",         fellBack);
		out.put("currentEstimates", new ArrayList<>(currentLatest.values()));
		out.put("priorYearFinal",   new ArrayList<>(priorLatest.values()));
		return out;
	}

	private void refreshYieldIfStale(String commodity, int currentYear, int priorYear) {
		Optional<YieldSnapshot> newest = yieldRepo.findFirstByCommodityOrderByFetchedAtDesc(commodity);
		boolean stale = newest.map(s -> {
			Duration age = Duration.between(s.getFetchedAt(), LocalDateTime.now());
			return age.toDays() >= REFRESH_DAYS;
		}).orElse(true);

		if (!stale) return;

		System.out.println("[USDA REPORTS] yield " + commodity + " cache stale, refreshing");
		doYieldRefresh(commodity, currentYear, priorYear);
	}

	/**
	 * Fetch yield + harvested acres for both current and prior year.
	 *
	 * Order matters: yield is fetched first so the snapshots exist when the
	 * harvested-acre pass attaches acres to them. Current-year harvested acres
	 * are what make the in-season national yield accurate — NASS publishes them
	 * monthly alongside the yield forecast.
	 */
	private void doYieldRefresh(String commodity, int currentYear, int priorYear) {
		final String c = commodity;
		safe(() -> fetchYieldForYear(c, currentYear),     "yield " + c + " " + currentYear);
		safe(() -> fetchYieldForYear(c, priorYear),       "yield " + c + " " + priorYear);
		safe(() -> fetchHarvestedForYear(c, currentYear), "harvested " + c + " " + currentYear);
		safe(() -> fetchHarvestedForYear(c, priorYear),   "harvested " + c + " " + priorYear);
	}

	/**
	 * Monthly Crop Production refresh.
	 *
	 * NASS publishes the Crop Production report — yield AND harvested-acre
	 * forecasts for corn / soybeans / wheat — around the 8th–12th of each month,
	 * with the final Crop Production Annual Summary in January. We fire on the
	 * 13th at 1 PM Eastern (safely after any same-month release + Quick Stats
	 * ingest) and force-refresh all three commodities regardless of cache age.
	 *
	 * Running year-round is harmless: outside the active forecast window NASS
	 * just returns the existing numbers and the upserts are idempotent. So a
	 * user opening the Corn / Soybeans / Wheat yield report always sees the
	 * latest USDA estimate with current-year harvested acres for the weighting.
	 */
	@Scheduled(cron = "0 0 13 13 * *", zone = "America/New_York")
	public void refreshMonthlyCropProduction() {
		int currentYear = Year.now().getValue();
		int priorYear   = currentYear - 1;
		System.out.println("[USDA REPORTS] monthly Crop Production refresh for " + currentYear);
		int ok = 0, fail = 0;
		for (String commodity : List.of("CORN", "SOYBEANS", "WHEAT")) {
			try {
				doYieldRefresh(commodity, currentYear, priorYear);
				ok++;
			} catch (Exception e) {
				fail++;
				System.err.println("[USDA REPORTS] monthly " + commodity + " failed: "
					+ e.getClass().getSimpleName() + " - " + e.getMessage());
			}
		}
		System.out.println("[USDA REPORTS] monthly refresh complete: " + ok + " ok, " + fail + " failed");
	}

	/**
	 * Pre-warm the yield cache shortly after startup so the first visitor to a
	 * Corn / Soybeans / Wheat report reads from the DB instead of paying for a
	 * live NASS fetch.
	 *
	 * Runs on a background thread so it never delays the server becoming ready,
	 * and goes through {@link #refreshYieldIfStale} — which is a no-op when the
	 * data is already < REFRESH_DAYS old. So restarting the server repeatedly in
	 * development only hits NASS on the first boot of each week, not every time.
	 */
	@EventListener(ApplicationReadyEvent.class)
	public void prewarmOnStartup() {
		Thread t = new Thread(() -> {
			int currentYear = Year.now().getValue();
			int priorYear   = currentYear - 1;
			System.out.println("[USDA REPORTS] startup pre-warm checking yield cache…");
			for (String commodity : List.of("CORN", "SOYBEANS", "WHEAT")) {
				try {
					refreshYieldIfStale(commodity, currentYear, priorYear);
				} catch (Exception e) {
					System.err.println("[USDA REPORTS] startup pre-warm " + commodity + " failed: "
						+ e.getClass().getSimpleName() + " - " + e.getMessage());
				}
			}
			System.out.println("[USDA REPORTS] startup pre-warm done");
		}, "usda-prewarm");
		t.setDaemon(true);
		t.start();
	}

	private void fetchYieldForYear(String commodity, int year) {
		URI uri = UriComponentsBuilder.fromHttpUrl(NASS_URL)
			.queryParam("key", apiKey)
			.queryParam("commodity_desc", commodity)
			.queryParam("statisticcat_desc", "YIELD")
			.queryParam("agg_level_desc", "STATE")
			.queryParam("source_desc", "SURVEY")
			.queryParam("year", year)
			.build().encode().toUri();

		System.out.println("[USDA REPORTS] YIELD " + commodity + " " + year + " → " + uri);
		ApiResponse resp = restTemplate.getForObject(uri, ApiResponse.class);
		if (resp == null || resp.getData() == null) return;

		LocalDateTime now = LocalDateTime.now();
		for (ApiItem item : resp.getData()) {
			if (!isGrainRow(item.getShortDesc())) continue;
			String state = item.getState();
			String refPeriod = normalizeRefPeriod(item.getReferencePeriodDesc());
			Double yieldBu = parseDouble(item.getYield());
			if (state == null || refPeriod == null || yieldBu == null) continue;

			YieldSnapshot s = yieldRepo
				.findByCommodityAndStateAndYearAndReferencePeriod(commodity, state, year, refPeriod)
				.orElseGet(YieldSnapshot::new);
			s.setCommodity(commodity);
			s.setState(state);
			s.setYear(year);
			s.setReferencePeriod(refPeriod);
			s.setYieldBu(yieldBu);
			s.setNassLoadTime(parseNassLoad(item.getLoad_time()));
			s.setFetchedAt(now);
			yieldRepo.save(s);
		}
	}

	/**
	 * Fetch AREA HARVESTED for a commodity/year across ALL reference periods and
	 * attach each value to the matching yield snapshot (same state + reference
	 * period). A yield snapshot whose period has no exact harvested match gets the
	 * state's latest harvested estimate as a fallback.
	 *
	 * This is what makes the production-weighted national yield correct:
	 *   national = Σ(state_yield × state_harvested_acres) / Σ(state_harvested_acres)
	 *
	 * Works for both the current year (in-season "YEAR - XXX FORECAST" periods)
	 * and prior year (the single "YEAR" final).
	 */
	private void fetchHarvestedForYear(String commodity, int year) {
		URI uri = UriComponentsBuilder.fromHttpUrl(NASS_URL)
			.queryParam("key", apiKey)
			.queryParam("commodity_desc", commodity)
			.queryParam("statisticcat_desc", "AREA HARVESTED")
			.queryParam("agg_level_desc", "STATE")
			.queryParam("source_desc", "SURVEY")
			.queryParam("year", year)
			.build().encode().toUri();

		System.out.println("[USDA REPORTS] AREA HARVESTED " + commodity + " " + year + " → " + uri);
		ApiResponse resp = restTemplate.getForObject(uri, ApiResponse.class);
		if (resp == null || resp.getData() == null) return;

		Map<String, Long> byStatePeriod    = new HashMap<>();  // "STATE|PERIOD" → acres
		Map<String, Long> latestByState    = new HashMap<>();  // STATE → latest acres
		Map<String, Integer> latestRank    = new HashMap<>();  // STATE → rank of latest
		for (ApiItem item : resp.getData()) {
			if (!isGrainRow(item.getShortDesc())) continue;
			String state = item.getState();
			String refPeriod = normalizeRefPeriod(item.getReferencePeriodDesc());
			Long acres = parseAcres(item.getYield());
			if (state == null || refPeriod == null || acres == null) continue;

			byStatePeriod.put(state + "|" + refPeriod, acres);
			int rk = REF_PERIOD_RANK.getOrDefault(refPeriod, 0);
			if (rk >= latestRank.getOrDefault(state, Integer.MIN_VALUE)) {
				latestRank.put(state, rk);
				latestByState.put(state, acres);
			}
		}
		if (byStatePeriod.isEmpty()) return;

		for (YieldSnapshot s : yieldRepo.findByCommodityAndYear(commodity, year)) {
			Long exact = byStatePeriod.get(s.getState() + "|" + s.getReferencePeriod());
			Long acres = exact != null ? exact : latestByState.get(s.getState());
			if (acres != null) {
				s.setAcres(acres);
				yieldRepo.save(s);
			}
		}
	}

	// ── PLANTING / ACREAGE ESTIMATOR ──────────────────────────────────────────

	public Map<String, Object> getPlantingData(String commodity) {
		commodity = commodity.toUpperCase();
		int currentYear = Year.now().getValue();
		int priorYear   = currentYear - 1;

		refreshPlantingIfStale(commodity, currentYear, priorYear);

		List<PlantingSnapshot> currentRows = plantingRepo.findByCommodityAndYear(commodity, currentYear);
		List<PlantingSnapshot> priorRows   = plantingRepo.findByCommodityAndYear(commodity, priorYear);

		boolean fellBack = false;
		if (currentRows.isEmpty()) {
			currentRows = priorRows;
			fellBack = true;
		}

		// For planting, we just take the latest reference period per state
		Map<String, PlantingSnapshot> latest = new HashMap<>();
		for (PlantingSnapshot p : currentRows) {
			PlantingSnapshot existing = latest.get(p.getState());
			if (existing == null) {
				latest.put(p.getState(), p);
			} else {
				LocalDateTime a = p.getNassLoadTime();
				LocalDateTime b = existing.getNassLoadTime();
				if (a != null && (b == null || a.isAfter(b))) latest.put(p.getState(), p);
			}
		}

		Map<String, PlantingSnapshot> priorLatest = new HashMap<>();
		for (PlantingSnapshot p : priorRows) {
			PlantingSnapshot existing = priorLatest.get(p.getState());
			if (existing == null) {
				priorLatest.put(p.getState(), p);
			} else {
				LocalDateTime a = p.getNassLoadTime();
				LocalDateTime b = existing.getNassLoadTime();
				if (a != null && (b == null || a.isAfter(b))) priorLatest.put(p.getState(), p);
			}
		}

		Map<String, Object> out = new HashMap<>();
		out.put("commodity",      commodity);
		out.put("currentYear",    fellBack ? priorYear : currentYear);
		out.put("priorYear",      priorYear);
		out.put("fellBack",       fellBack);
		out.put("currentPlantings", new ArrayList<>(latest.values()));
		out.put("priorYearPlantings", new ArrayList<>(priorLatest.values()));
		return out;
	}

	private void refreshPlantingIfStale(String commodity, int currentYear, int priorYear) {
		Optional<PlantingSnapshot> newest = plantingRepo.findFirstByCommodityOrderByFetchedAtDesc(commodity);
		boolean stale = newest.map(s -> {
			Duration age = Duration.between(s.getFetchedAt(), LocalDateTime.now());
			return age.toDays() >= REFRESH_DAYS;
		}).orElse(true);

		if (!stale) return;

		System.out.println("[USDA REPORTS] planting " + commodity + " cache stale, refreshing");
		final String c = commodity;
		safe(() -> fetchPlantingForYear(c, currentYear), "planting " + c + " " + currentYear);
		safe(() -> fetchPlantingForYear(c, priorYear),   "planting " + c + " " + priorYear);
	}

	/**
	 * USDA publishes the <strong>Acreage</strong> report at <strong>noon Eastern on June 30</strong>
	 * — this is the refined estimate of actual planted acres for the current crop year.
	 * We fire 15 minutes after release so NASS Quick Stats has time to ingest the new
	 * numbers, then force-refresh corn / soybeans / wheat planting data unconditionally
	 * (bypassing the staleness gate).
	 */
	@Scheduled(cron = "0 15 12 30 6 *", zone = "America/New_York")
	public void refreshJuneAcreageReport() {
		int year = Year.now().getValue();
		System.out.println("[USDA REPORTS] June 30 Acreage report — refreshing for " + year);
		refreshPlantingForReport(year, "Acreage");
	}

	/**
	 * <strong>Prospective Plantings</strong> report, the first farmer-intent number of the
	 * year. Released noon Eastern on March 31. Same 15-minute buffer.
	 */
	@Scheduled(cron = "0 15 12 31 3 *", zone = "America/New_York")
	public void refreshMarchPlantingsReport() {
		int year = Year.now().getValue();
		System.out.println("[USDA REPORTS] March 31 Prospective Plantings report — refreshing for " + year);
		refreshPlantingForReport(year, "Plantings");
	}

	/** Force-fetch planted acres for each tracked grain commodity, swallowing per-commodity errors. */
	private void refreshPlantingForReport(int year, String reportLabel) {
		int ok = 0, fail = 0;
		for (String commodity : List.of("CORN", "SOYBEANS", "WHEAT")) {
			try {
				fetchPlantingForYear(commodity, year);
				ok++;
			} catch (Exception e) {
				fail++;
				System.err.println("[USDA REPORTS] " + reportLabel + " " + commodity + " " + year
					+ " failed: " + e.getClass().getSimpleName() + " - " + e.getMessage());
			}
		}
		System.out.println("[USDA REPORTS] " + reportLabel + " report refresh complete: "
			+ ok + " ok, " + fail + " failed");
	}

	private void fetchPlantingForYear(String commodity, int year) {
		URI uri = UriComponentsBuilder.fromHttpUrl(NASS_URL)
			.queryParam("key", apiKey)
			.queryParam("commodity_desc", commodity)
			.queryParam("statisticcat_desc", "AREA PLANTED")
			.queryParam("agg_level_desc", "STATE")
			.queryParam("source_desc", "SURVEY")
			.queryParam("year", year)
			.build().encode().toUri();

		System.out.println("[USDA REPORTS] AREA PLANTED " + commodity + " " + year + " → " + uri);
		ApiResponse resp = restTemplate.getForObject(uri, ApiResponse.class);
		if (resp == null || resp.getData() == null) return;

		LocalDateTime now = LocalDateTime.now();
		for (ApiItem item : resp.getData()) {
			if (!isGrainRow(item.getShortDesc())) continue;
			String state = item.getState();
			String refPeriod = normalizeRefPeriod(item.getReferencePeriodDesc());
			Long acres = parseAcres(item.getYield());
			if (state == null || refPeriod == null || acres == null) continue;

			PlantingSnapshot p = plantingRepo
				.findByCommodityAndStateAndYearAndReferencePeriod(commodity, state, year, refPeriod)
				.orElseGet(PlantingSnapshot::new);
			p.setCommodity(commodity);
			p.setState(state);
			p.setYear(year);
			p.setReferencePeriod(refPeriod);
			p.setAcres(acres);
			p.setNassLoadTime(parseNassLoad(item.getLoad_time()));
			p.setFetchedAt(now);
			plantingRepo.save(p);
		}
	}

	// ── SHARED HELPERS ────────────────────────────────────────────────────────

	/** Skip non-grain rows (silage, sweet corn, soy oil, etc) — we want the headline grain numbers. */
	private static boolean isGrainRow(String shortDesc) {
		if (shortDesc == null) return true;
		String s = shortDesc.toUpperCase();
		if (s.contains("SILAGE")) return false;
		if (s.contains("SWEET"))  return false;
		if (s.contains("FOR OIL")) return false;
		if (s.contains("FOR BEANS")) return false;
		return true;
	}

	private Map<String, YieldSnapshot> pickLatestPerState(List<YieldSnapshot> rows) {
		Map<String, YieldSnapshot> out = new HashMap<>();
		for (YieldSnapshot s : rows) {
			YieldSnapshot existing = out.get(s.getState());
			if (existing == null || rank(s) > rank(existing)) {
				out.put(s.getState(), s);
			}
		}
		return out;
	}

	private int rank(YieldSnapshot s) {
		String p = s.getReferencePeriod();
		return p == null ? 0 : REF_PERIOD_RANK.getOrDefault(p, 0);
	}

	/**
	 *   "YEAR"                  → "YEAR"
	 *   "YEAR - AUG FORECAST"   → "AUG"
	 *   "MARKETING YEAR"        → null
	 */
	private static String normalizeRefPeriod(String raw) {
		if (raw == null) return null;
		String r = raw.trim().toUpperCase();
		if (r.equals("YEAR")) return "YEAR";
		if (r.equals("MARKETING YEAR")) return null;
		if (r.startsWith("YEAR - ") && r.endsWith(" FORECAST")) {
			String month = r.substring("YEAR - ".length(), r.length() - " FORECAST".length()).trim();
			if (REF_PERIOD_RANK.containsKey(month)) return month;
		}
		if (REF_PERIOD_RANK.containsKey(r)) return r;
		return null;
	}

	private static Double parseDouble(String s) {
		if (s == null) return null;
		try { return Double.parseDouble(s.replace(",", "").trim()); }
		catch (Exception e) { return null; }
	}

	private static Long parseAcres(String s) {
		if (s == null) return null;
		try { return Long.parseLong(s.replace(",", "").trim()); }
		catch (Exception e) {
			Double d = parseDouble(s);
			return d == null ? null : d.longValue();
		}
	}

	private static LocalDateTime parseNassLoad(String s) {
		if (s == null) return null;
		try { return LocalDateTime.parse(s, NASS_LOAD_FMT); }
		catch (Exception e) { return null; }
	}

	private static void safe(Runnable r, String label) {
		try { r.run(); }
		catch (Exception e) {
			System.err.println("[USDA REPORTS] " + label + " failed: "
				+ e.getClass().getSimpleName() + " - " + e.getMessage());
		}
	}
}
