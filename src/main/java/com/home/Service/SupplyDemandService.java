package com.home.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.transaction.Transactional;

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

import com.home.Domain.SupplyDemand;
import com.home.Domain.WasdeFile;
import com.home.Repository.SupplyDemandRepository;
import com.home.Repository.WasdeFileRepository;

/**
 * USDA supply/demand balance sheets — the actual WASDE numbers.
 *
 * Ingests USDA OCE's monthly machine-readable WASDE CSV
 * (oce-wasde-report-data-YYYY-MM-Vn.csv), filters it to Corn / Soybeans / Wheat,
 * U.S. + World, and caches the exact published values (already in WASDE units —
 * U.S. in million bushels / acres, World in MMT). No conversion, so the figures
 * match the WASDE table line-for-line.
 *
 * Parsing is deliberately tolerant (fuzzy header matching, file-order preserved)
 * because the CSV can only be verified against the live USDA host.
 */
@Service
public class SupplyDemandService {

	private static final String BASE_URL = "https://www.usda.gov/sites/default/files/documents/";
	private static final int STALE_DAYS = 20;

	/** Display key per commodity. */
	private static final String[] COMMODITIES = { "CORN", "SOYBEANS", "WHEAT", "SOYBEAN_MEAL", "SOYBEAN_OIL", "COTTON" };

	private final RestTemplate restTemplate;
	private final SupplyDemandRepository repo;
	private final WasdeFileRepository wasdeFileRepo;

	/** Optional hard override of the CSV URL (e.g. pin a specific month/version). */
	@Value("${WASDE_CSV_URL:}")
	private String csvUrlOverride;

	/**
	 * Optional local source — usda.gov blocks server-side downloads, so this is the
	 * reliable path: a CSV file, or a folder we read the newest WASDE CSV from.
	 */
	@Value("${WASDE_CSV_PATH:}")
	private String csvPath;

	public SupplyDemandService(RestTemplate restTemplate, SupplyDemandRepository repo,
			WasdeFileRepository wasdeFileRepo) {
		this.restTemplate = restTemplate;
		this.repo = repo;
		this.wasdeFileRepo = wasdeFileRepo;
	}

	/* ── Scheduling ─────────────────────────────────────────────────────── */

	@EventListener(ApplicationReadyEvent.class)
	public void prewarm() {
		new Thread(() -> {
			try {
				// Re-ingest if the cache is empty/stale/untagged, missing a commodity
				// (newly-added meal/oil), or missing the South America regions.
				boolean missing = !repo.existsByCommodityAndRegion("SOYBEANS", "BRAZIL");
				for (String c : COMMODITIES) if (repo.countByCommodity(c) == 0) { missing = true; break; }
				if (repo.count() == 0 || isStale() || repo.existsByMonthIsNull() || missing) ingestAll();
			} catch (Exception e) {
				System.err.println("[WASDE] startup ingest failed: " + e.getMessage());
			}
		}, "wasde-prewarm").start();
	}

	/** Day after the monthly WASDE release. */
	@Scheduled(cron = "0 0 13 13 * *", zone = "America/Chicago")
	public void scheduledIngest() {
		try { ingestAll(); } catch (Exception e) {
			System.err.println("[WASDE] scheduled ingest failed: " + e.getMessage());
		}
	}

	private boolean isStale() {
		return repo.findAll().stream()
			.map(SupplyDemand::getUpdatedAt).filter(t -> t != null)
			.max(LocalDateTime::compareTo)
			.map(t -> t.isBefore(LocalDateTime.now().minusDays(STALE_DAYS)))
			.orElse(true);
	}

	/* ── Download + parse ───────────────────────────────────────────────── */

	@Transactional
	public void ingestAll() {
		List<String> csvs = collectCsvs();
		if (csvs.isEmpty()) {
			System.err.println("[WASDE] could not find a WASDE CSV (set WASDE_CSV_PATH or WASDE_CSV_URL)");
			return;
		}
		// Parse every monthly file we have so the dashboard can show month-over-month
		// changes. Seq is shared across files so a given attribute keeps a stable row
		// order regardless of which month it first appeared in.
		Map<String, List<SupplyDemand>> byCommodity = new LinkedHashMap<>();
		Map<String, Integer> seqByAttr = new HashMap<>();
		for (String csv : csvs) parseInto(csv, byCommodity, seqByAttr);
		if (byCommodity.isEmpty()) {
			System.err.println("[WASDE] parsed 0 usable rows — check the CSV column layout");
			return;
		}
		for (String c : COMMODITIES) {
			List<SupplyDemand> rows = byCommodity.get(c);
			if (rows == null || rows.isEmpty()) continue;
			// De-dupe by (region, attribute, month) keeping the last — so if the same
			// month appears in more than one source (e.g. a bundled file and an admin
			// upload), it isn't double-counted.
			Map<String, SupplyDemand> dedup = new LinkedHashMap<>();
			for (SupplyDemand r : rows) dedup.put(r.getRegion() + "|" + r.getAttribute() + "|" + r.getMonth(), r);
			List<SupplyDemand> finalRows = new ArrayList<>(dedup.values());
			repo.deleteByCommodity(c);
			repo.saveAll(finalRows);
			System.out.println("[WASDE] cached " + finalRows.size() + " rows for " + c);
		}
	}

	/* ── Admin upload ───────────────────────────────────────────────────── */

	/**
	 * Store an admin-uploaded WASDE CSV in the DB (durable) and re-ingest so the
	 * new month shows immediately. Validates the content looks like a WASDE file.
	 */
	@Transactional
	public Map<String, Object> uploadAndIngest(String filename, String content) {
		Map<String, Object> out = new LinkedHashMap<>();
		if (content == null || content.length() < 100 || !content.toLowerCase().contains("commodity")) {
			out.put("ok", false);
			out.put("message", "That doesn't look like a WASDE machine-readable CSV.");
			return out;
		}
		String name = (filename == null || filename.isBlank()) ? "wasde-upload.csv" : filename.trim();
		if (!name.toLowerCase().contains("wasde")) name = "wasde-" + name;

		WasdeFile wf = wasdeFileRepo.findByFilename(name).orElseGet(WasdeFile::new);
		wf.setFilename(name);
		wf.setContent(content);
		wf.setMonthKey(monthFromName(name));
		wf.setUploadedAt(LocalDateTime.now());
		wasdeFileRepo.save(wf);

		ingestAll();

		out.put("ok", true);
		out.put("filename", name);
		out.put("monthsLoaded", repo.findDistinctMonths());
		out.put("uploads", listUploads());
		return out;
	}

	/** Months currently loaded + admin uploads on record — for the admin status view. */
	public Map<String, Object> uploadStatus() {
		Map<String, Object> out = new LinkedHashMap<>();
		out.put("monthsLoaded", repo.findDistinctMonths());
		out.put("uploads", listUploads());
		return out;
	}

	private List<Map<String, Object>> listUploads() {
		List<Map<String, Object>> list = new ArrayList<>();
		for (WasdeFile wf : wasdeFileRepo.findAll()) {
			Map<String, Object> m = new LinkedHashMap<>();
			m.put("filename", wf.getFilename());
			m.put("monthKey", wf.getMonthKey());
			m.put("uploadedAt", wf.getUploadedAt() == null ? null : wf.getUploadedAt().toString());
			list.add(m);
		}
		return list;
	}

	private static Integer monthFromName(String name) {
		Matcher m = Pattern.compile("(\\d{4})-(\\d{2})").matcher(name);
		if (m.find()) return Integer.parseInt(m.group(1)) * 100 + Integer.parseInt(m.group(2));
		return null;
	}

	/** All WASDE CSVs we can read: every *.csv in the local folder, else a single download. */
	private List<String> collectCsvs() {
		List<String> out = new ArrayList<>();
		if (csvPath != null && !csvPath.isBlank()) {
			try {
				Path p = Path.of(csvPath.trim());
				if (Files.isDirectory(p)) {
					try (var stream = Files.list(p)) {
						List<Path> files = stream
							.filter(f -> {
								String n = f.getFileName().toString().toLowerCase();
								return n.endsWith(".csv") && n.contains("wasde");
							})
							.sorted(Comparator.comparingLong(f -> f.toFile().lastModified()))
							.toList();
						for (Path f : files) {
							System.out.println("[WASDE] reading local file " + f);
							out.add(new String(Files.readAllBytes(f), StandardCharsets.ISO_8859_1));
						}
					}
				}
			} catch (Exception e) {
				System.err.println("[WASDE] local folder read failed: " + e.getMessage());
			}
		}
		// Admin-uploaded CSVs stored in the DB (durable across redeploys). Appended
		// after bundled files so a re-uploaded month wins the de-dupe.
		try {
			for (WasdeFile wf : wasdeFileRepo.findAll()) {
				if (wf.getContent() != null && !wf.getContent().isBlank()) {
					System.out.println("[WASDE] reading uploaded file " + wf.getFilename());
					out.add(wf.getContent());
				}
			}
		} catch (Exception e) {
			System.err.println("[WASDE] uploaded-file read failed: " + e.getMessage());
		}
		if (!out.isEmpty()) return out;
		// Nothing local/uploaded — fall back to a single remote/pinned download.
		String one = downloadCsv();
		if (one != null) out.add(one);
		return out;
	}

	/** Local file/folder first (reliable), then a pinned URL, then auto-constructed URLs. */
	private String downloadCsv() {
		String local = readLocal();
		if (local != null) return local;

		if (csvUrlOverride != null && !csvUrlOverride.isBlank()) {
			return fetch(csvUrlOverride.trim());
		}
		LocalDate now = LocalDate.now();
		for (int back = 0; back <= 2; back++) {
			YearMonth ym = YearMonth.from(now).minusMonths(back);
			for (String suf : new String[] { "-V2", "-V1", "-V3", "" }) {
				String url = String.format("%soce-wasde-report-data-%d-%02d%s.csv",
					BASE_URL, ym.getYear(), ym.getMonthValue(), suf);
				String body = fetch(url);
				if (body != null && body.length() > 100 && body.toLowerCase().contains("commodity")) {
					System.out.println("[WASDE] using " + url);
					return body;
				}
			}
		}
		return null;
	}

	/** Read the WASDE CSV from WASDE_CSV_PATH — a file, or a folder's newest *.csv. */
	private String readLocal() {
		if (csvPath == null || csvPath.isBlank()) return null;
		try {
			Path p = Path.of(csvPath.trim());
			Path file = p;
			if (Files.isDirectory(p)) {
				try (var stream = Files.list(p)) {
					file = stream
						.filter(f -> {
							String n = f.getFileName().toString().toLowerCase();
							return n.endsWith(".csv") && n.contains("wasde");
						})
						.max(Comparator.comparingLong(f -> f.toFile().lastModified()))
						.orElse(null);
				}
			}
			if (file == null || !Files.exists(file)) {
				System.err.println("[WASDE] WASDE_CSV_PATH has no CSV: " + csvPath);
				return null;
			}
			System.out.println("[WASDE] reading local file " + file);
			// ISO-8859-1 never throws on odd bytes; the CSV is ASCII anyway.
			return new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1);
		} catch (Exception e) {
			System.err.println("[WASDE] local read failed: " + e.getMessage());
			return null;
		}
	}

	private String fetch(String url) {
		try {
			HttpHeaders h = new HttpHeaders();
			h.set("User-Agent",
				"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
				+ "Chrome/124.0.0.0 Safari/537.36");
			h.set("Accept", "text/csv,application/octet-stream,*/*");
			ResponseEntity<String> resp = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(h), String.class);
			if (resp.getStatusCode().is2xxSuccessful()) return resp.getBody();
			System.err.println("[WASDE] " + url + " -> HTTP " + resp.getStatusCode());
			return null;
		} catch (Exception e) {
			System.err.println("[WASDE] fetch " + url + " failed: " + e.getClass().getSimpleName() + " " + e.getMessage());
			return null;
		}
	}

	/**
	 * Parse one monthly CSV, appending rows to {@code out} and reusing {@code seqByAttr}
	 * so attribute row-order stays stable across months. Each row is tagged with a
	 * sortable snapshot key (YYYYMM in {@code month}) so the API can find the prior month.
	 */
	private void parseInto(String csv, Map<String, List<SupplyDemand>> out, Map<String, Integer> seqByAttr) {
		String[] lines = csv.split("\r?\n");
		if (lines.length < 2) return;

		List<String> header = parseLine(lines[0]);
		int iComm = col(header, "commodity");
		int iReg  = col(header, "region");
		int iAttr = col(header, "attribute");
		int iVal  = col(header, "value");
		int iUnit = col(header, "unit");
		int iYear = col(header, "marketyear", "year");
		int iDate = col(header, "reportdate", "releasedate");
		int iFYear = col(header, "forecastyear");
		int iFMon  = col(header, "forecastmonth");
		if (iComm < 0 || iReg < 0 || iAttr < 0 || iVal < 0 || iYear < 0) {
			System.err.println("[WASDE] header missing required columns: " + header);
			return;
		}

		for (int i = 1; i < lines.length; i++) {
			if (lines[i].isBlank()) continue;
			List<String> f = parseLine(lines[i]);
			if (f.size() <= Math.max(iComm, Math.max(iReg, Math.max(iAttr, Math.max(iVal, iYear))))) continue;

			String commodity = mapCommodity(get(f, iComm));
			if (commodity == null) continue;
			String region = mapRegion(get(f, iReg));
			if (region == null) continue;
			Integer year = parseYear(get(f, iYear));
			if (year == null) continue;
			Double value = parseNum(get(f, iVal));
			if (value == null) continue;

			String attribute = get(f, iAttr).trim();
			String reportDate = iDate >= 0 ? get(f, iDate).trim() : null;
			String key = commodity + "|" + region + "|" + attribute;
			int seq = seqByAttr.computeIfAbsent(key, k -> seqByAttr.size());

			SupplyDemand sd = new SupplyDemand();
			sd.setCommodity(commodity);
			sd.setRegion(region);
			sd.setMarketYear(year);
			sd.setAttribute(attribute);
			sd.setValue(value);
			sd.setUnit(iUnit >= 0 ? get(f, iUnit).trim() : "");
			sd.setReportDate(reportDate);
			sd.setMonth(snapshotKey(get(f, iFYear), get(f, iFMon), reportDate));
			sd.setSeq(seq);
			out.computeIfAbsent(commodity, k -> new ArrayList<>()).add(sd);
		}
	}

	private static final Map<String, Integer> MONTHS = Map.ofEntries(
		Map.entry("jan", 1), Map.entry("feb", 2), Map.entry("mar", 3), Map.entry("apr", 4),
		Map.entry("may", 5), Map.entry("jun", 6), Map.entry("jul", 7), Map.entry("aug", 8),
		Map.entry("sep", 9), Map.entry("oct", 10), Map.entry("nov", 11), Map.entry("dec", 12));

	/** Sortable snapshot id (YYYYMM) from forecast year/month, falling back to "Month YYYY". */
	private static Integer snapshotKey(String fyear, String fmon, String reportDate) {
		Integer y = parseYear(fyear);
		Integer m = parseNum(fmon) != null ? parseNum(fmon).intValue() : null;
		if (y == null && reportDate != null) y = parseYear(reportDate);
		if (m == null && reportDate != null) {
			String n = norm(reportDate);
			for (var e : MONTHS.entrySet()) if (n.startsWith(e.getKey()) || n.contains(e.getKey())) { m = e.getValue(); break; }
		}
		if (y == null || m == null) return null;
		return y * 100 + m;
	}

	/* ── Read for the API ───────────────────────────────────────────────── */

	public Map<String, Object> getBalanceSheet(String commodity) {
		commodity = commodity.toUpperCase();
		List<SupplyDemand> rows = repo.findByCommodityOrderByRegionAscSeqAsc(commodity);

		Map<String, Object> out = new LinkedHashMap<>();
		out.put("commodity", commodity);
		if (rows.isEmpty()) {
			out.put("years", List.of());
			out.put("regions", Map.of());
			out.put("message", "WASDE data isn't loaded yet.");
			return out;
		}

		// Snapshots are tagged by month (YYYYMM). The newest snapshot is what we display;
		// the one before it supplies the month-over-month comparison for the new-crop year.
		Integer latestMonth = rows.stream().map(SupplyDemand::getMonth)
			.filter(java.util.Objects::nonNull).max(Integer::compareTo).orElse(null);
		Integer prevMonth = rows.stream().map(SupplyDemand::getMonth)
			.filter(m -> m != null && (latestMonth == null || m < latestMonth))
			.max(Integer::compareTo).orElse(null);

		List<SupplyDemand> current = rows.stream()
			.filter(r -> latestMonth == null || latestMonth.equals(r.getMonth())).toList();
		List<SupplyDemand> prev = prevMonth == null ? List.of()
			: rows.stream().filter(r -> prevMonth.equals(r.getMonth())).toList();

		// Latest up-to-3 marketing years (newest first) from the current snapshot.
		TreeSet<Integer> ys = new TreeSet<>(java.util.Collections.reverseOrder());
		String reportDate = null;
		LocalDateTime updated = null;
		for (SupplyDemand r : current) {
			ys.add(r.getMarketYear());
			if (reportDate == null && r.getReportDate() != null) reportDate = r.getReportDate();
			if (r.getUpdatedAt() != null && (updated == null || r.getUpdatedAt().isAfter(updated))) updated = r.getUpdatedAt();
		}
		List<Integer> years = new ArrayList<>(ys).subList(0, Math.min(3, ys.size()));
		int newestYear = years.isEmpty() ? Integer.MIN_VALUE : years.get(0);

		out.put("years", years);
		out.put("reportDate", reportDate);
		out.put("prevReportDate", prev.isEmpty() ? null : prev.get(0).getReportDate());
		out.put("updatedAt", updated == null ? null : updated.toString());

		// One balance sheet per region we recognize; only include those with data
		// (e.g. corn has Brazil/Argentina, soybeans adds Paraguay, meal/oil neither).
		Map<String, Object> regions = new LinkedHashMap<>();
		for (String reg : new String[] { "US", "WORLD", "BRAZIL", "ARGENTINA", "PARAGUAY" }) {
			List<Map<String, Object>> regionRows = buildRegion(current, prev, reg, years, newestYear);
			if (!regionRows.isEmpty()) regions.put(reg, regionRows);
		}
		// Combined "All South America" total when 2+ SA countries are present.
		List<String> saKeys = new ArrayList<>();
		for (String k : new String[] { "BRAZIL", "ARGENTINA", "PARAGUAY" }) if (regions.containsKey(k)) saKeys.add(k);
		if (saKeys.size() >= 2) {
			List<Map<String, Object>> agg = buildRegionAggregate(current, prev, saKeys, years, newestYear);
			if (!agg.isEmpty()) regions.put("SOUTH_AMERICA", agg);
		}
		out.put("regions", regions);
		return out;
	}

	private List<Map<String, Object>> buildRegion(List<SupplyDemand> current, List<SupplyDemand> prev,
			String region, List<Integer> years, int newestYear) {
		// Previous month's value for the new-crop year, per attribute (for the change note).
		Map<String, Double> prevByAttr = new HashMap<>();
		for (SupplyDemand r : prev) {
			if (!region.equals(r.getRegion())) continue;
			if ("US".equals(region) && isMetric(r.getUnit())) continue;
			if (r.getMarketYear() != null && r.getMarketYear() == newestYear) prevByAttr.put(r.getAttribute(), r.getValue());
		}

		Map<String, Map<String, Object>> byAttr = new LinkedHashMap<>();
		for (SupplyDemand r : current) {
			if (!region.equals(r.getRegion())) continue;
			// WASDE lists "United States" both as the U.S. balance sheet (bushels)
			// and as a metric line inside the World table. Keep only the bushel/acre
			// rows for the U.S. view so nothing shows in metric tons.
			if ("US".equals(region) && isMetric(r.getUnit())) continue;
			Map<String, Object> a = byAttr.computeIfAbsent(r.getAttribute(), k -> {
				Map<String, Object> m = new LinkedHashMap<>();
				m.put("attribute", r.getAttribute());
				m.put("unit", r.getUnit());
				m.put("seq", r.getSeq());
				m.put("values", new ArrayList<Double>(java.util.Collections.nCopies(years.size(), null)));
				m.put("prev", prevByAttr.get(r.getAttribute()));   // new-crop year, prior month (nullable)
				return m;
			});
			int idx = years.indexOf(r.getMarketYear());
			if (idx >= 0) {
				@SuppressWarnings("unchecked")
				List<Double> vals = (List<Double>) a.get("values");
				vals.set(idx, r.getValue());
			}
		}
		List<Map<String, Object>> list = new ArrayList<>(byAttr.values());
		list.sort((x, y) -> Integer.compare((int) x.get("seq"), (int) y.get("seq")));
		return list;
	}

	/**
	 * Combined balance sheet that sums several regions into one (e.g. "All South
	 * America" = Brazil + Argentina + Paraguay). Only additive quantities are
	 * totaled — per-acre yield and prices can't be summed, so they're dropped.
	 */
	private List<Map<String, Object>> buildRegionAggregate(List<SupplyDemand> current, List<SupplyDemand> prev,
			List<String> regionKeys, List<Integer> years, int newestYear) {
		Map<String, Double> prevByAttr = new HashMap<>();
		for (SupplyDemand r : prev) {
			if (!regionKeys.contains(r.getRegion()) || !isAdditive(r.getUnit()) || r.getValue() == null) continue;
			if (r.getMarketYear() != null && r.getMarketYear() == newestYear) prevByAttr.merge(r.getAttribute(), r.getValue(), Double::sum);
		}

		Map<String, Map<String, Object>> byAttr = new LinkedHashMap<>();
		for (SupplyDemand r : current) {
			if (!regionKeys.contains(r.getRegion()) || !isAdditive(r.getUnit())) continue;
			Map<String, Object> a = byAttr.computeIfAbsent(r.getAttribute(), k -> {
				Map<String, Object> m = new LinkedHashMap<>();
				m.put("attribute", r.getAttribute());
				m.put("unit", r.getUnit());
				m.put("seq", r.getSeq());
				m.put("values", new ArrayList<Double>(java.util.Collections.nCopies(years.size(), null)));
				m.put("prev", prevByAttr.get(r.getAttribute()));
				return m;
			});
			int idx = years.indexOf(r.getMarketYear());
			if (idx >= 0 && r.getValue() != null) {
				@SuppressWarnings("unchecked")
				List<Double> vals = (List<Double>) a.get("values");
				Double cur = vals.get(idx);
				vals.set(idx, (cur == null ? 0.0 : cur) + r.getValue());
			}
		}
		List<Map<String, Object>> list = new ArrayList<>(byAttr.values());
		list.sort((x, y) -> Integer.compare((int) x.get("seq"), (int) y.get("seq")));
		return list;
	}

	/** Additive quantity unit (tons, hectares) vs. a rate/price (MT/HA, $/bu, %). */
	private static boolean isAdditive(String unit) {
		String u = unit == null ? "" : unit.toLowerCase();
		if (u.isBlank()) return false;
		return !(u.contains("/") || u.contains(" per ") || u.startsWith("$")
			|| u.contains("percent") || u.contains("dollar") || u.contains("cent"));
	}

	/* ── CSV + value helpers ────────────────────────────────────────────── */

	/** Find a column by normalized header name (exact first, then contains). */
	private static int col(List<String> header, String... keys) {
		for (String k : keys) {
			for (int i = 0; i < header.size(); i++) {
				if (norm(header.get(i)).equals(k)) return i;
			}
		}
		for (String k : keys) {
			for (int i = 0; i < header.size(); i++) {
				if (norm(header.get(i)).contains(k)) return i;
			}
		}
		return -1;
	}

	private static String mapCommodity(String c) {
		String n = norm(c);
		if (n.equals("corn")) return "CORN";
		if (n.equals("wheat")) return "WHEAT";
		// Soybean appears three ways: the oilseed itself ("Oilseed, Soybean") and its
		// two crush products, each published under two names (US table + World table):
		//   meal → "Meal, Soybean" / "Soybean Meal"
		//   oil  → "Oil, Soybean"  / "Soybean Oil"
		// Note "oilseed" contains "oil", so check the oil-product spellings explicitly.
		if (n.contains("soybean")) {
			if (n.contains("meal")) return "SOYBEAN_MEAL";
			if (n.contains("oilsoybean") || n.contains("soybeanoil")) return "SOYBEAN_OIL";
			return "SOYBEANS";
		}
		if (n.contains("cotton")) return "COTTON";   // WASDE: "Cotton" (million 480-lb bales)
		return null;
	}

	private static String mapRegion(String r) {
		String n = norm(r);
		if (n.equals("unitedstates") || n.equals("us")) return "US";
		if (n.equals("world")) return "WORLD";
		if (n.equals("argentina")) return "ARGENTINA";
		if (n.equals("brazil")) return "BRAZIL";
		if (n.equals("paraguay")) return "PARAGUAY";
		return null;
	}

	private static final Pattern YEAR = Pattern.compile("(\\d{4})");
	private static Integer parseYear(String s) {
		if (s == null) return null;
		Matcher m = YEAR.matcher(s);
		return m.find() ? Integer.parseInt(m.group(1)) : null;
	}

	private static Double parseNum(String s) {
		if (s == null) return null;
		String t = s.replace(",", "").replace("$", "").trim();
		if (t.isEmpty() || t.equalsIgnoreCase("NA") || t.equals("-")) return null;
		try { return Double.parseDouble(t); } catch (Exception e) { return null; }
	}

	private static String norm(String s) {
		return s == null ? "" : s.toLowerCase().replaceAll("[^a-z0-9]", "");
	}

	/**
	 * True for the metric World-table units we hide from the U.S. (native-unit) view —
	 * Million/1000 Metric Tons, MT/HA, Hectares. Short tons and pounds are the native
	 * U.S. units for soybean meal / oil, so they are explicitly kept.
	 */
	private static boolean isMetric(String unit) {
		String n = norm(unit);
		if (n.contains("shortton") || n.contains("pound")) return false;
		return n.contains("metricton") || n.contains("tonne") || n.contains("hectare") || n.contains("mt");
	}

	private static String get(List<String> f, int i) {
		return (i >= 0 && i < f.size()) ? f.get(i) : "";
	}

	/** Minimal RFC-4180 CSV field splitter (handles quoted fields + escaped quotes). */
	private static List<String> parseLine(String line) {
		List<String> out = new ArrayList<>();
		StringBuilder cur = new StringBuilder();
		boolean inQ = false;
		for (int i = 0; i < line.length(); i++) {
			char c = line.charAt(i);
			if (inQ) {
				if (c == '"') {
					if (i + 1 < line.length() && line.charAt(i + 1) == '"') { cur.append('"'); i++; }
					else inQ = false;
				} else cur.append(c);
			} else {
				if (c == '"') inQ = true;
				else if (c == ',') { out.add(cur.toString()); cur.setLength(0); }
				else cur.append(c);
			}
		}
		out.add(cur.toString());
		return out;
	}
}
