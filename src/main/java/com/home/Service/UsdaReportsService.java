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
		final String c = commodity;
		safe(() -> fetchYieldForYear(c, currentYear), "yield " + c + " " + currentYear);
		safe(() -> fetchYieldForYear(c, priorYear),   "yield " + c + " " + priorYear);
		safe(() -> fetchAcresHarvestedForYear(c, priorYear), "acres harv " + c + " " + priorYear);
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

	private void fetchAcresHarvestedForYear(String commodity, int year) {
		URI uri = UriComponentsBuilder.fromHttpUrl(NASS_URL)
			.queryParam("key", apiKey)
			.queryParam("commodity_desc", commodity)
			.queryParam("statisticcat_desc", "AREA HARVESTED")
			.queryParam("agg_level_desc", "STATE")
			.queryParam("source_desc", "SURVEY")
			.queryParam("reference_period_desc", "YEAR")
			.queryParam("year", year)
			.build().encode().toUri();

		System.out.println("[USDA REPORTS] AREA HARVESTED " + commodity + " " + year + " → " + uri);
		ApiResponse resp = restTemplate.getForObject(uri, ApiResponse.class);
		if (resp == null || resp.getData() == null) return;

		for (ApiItem item : resp.getData()) {
			if (!isGrainRow(item.getShortDesc())) continue;
			String state = item.getState();
			Long acres = parseAcres(item.getYield());
			if (state == null || acres == null) continue;

			final String st = state;
			final Long ac = acres;
			yieldRepo.findByCommodityAndYear(commodity, year).stream()
				.filter(s -> st.equals(s.getState()))
				.forEach(s -> { s.setAcres(ac); yieldRepo.save(s); });
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
