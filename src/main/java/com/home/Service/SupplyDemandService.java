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
import com.home.Repository.SupplyDemandRepository;

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
	private static final String[] COMMODITIES = { "CORN", "SOYBEANS", "WHEAT" };

	private final RestTemplate restTemplate;
	private final SupplyDemandRepository repo;

	/** Optional hard override of the CSV URL (e.g. pin a specific month/version). */
	@Value("${WASDE_CSV_URL:}")
	private String csvUrlOverride;

	/**
	 * Optional local source — usda.gov blocks server-side downloads, so this is the
	 * reliable path: a CSV file, or a folder we read the newest WASDE CSV from.
	 */
	@Value("${WASDE_CSV_PATH:}")
	private String csvPath;

	public SupplyDemandService(RestTemplate restTemplate, SupplyDemandRepository repo) {
		this.restTemplate = restTemplate;
		this.repo = repo;
	}

	/* ── Scheduling ─────────────────────────────────────────────────────── */

	@EventListener(ApplicationReadyEvent.class)
	public void prewarm() {
		new Thread(() -> {
			try {
				if (repo.count() == 0 || isStale()) ingestAll();
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
		String csv = downloadCsv();
		if (csv == null) {
			System.err.println("[WASDE] could not download a WASDE CSV (set WASDE_CSV_URL to pin one)");
			return;
		}
		Map<String, List<SupplyDemand>> byCommodity = parse(csv);
		if (byCommodity.isEmpty()) {
			System.err.println("[WASDE] parsed 0 usable rows — check the CSV column layout");
			return;
		}
		for (String c : COMMODITIES) {
			List<SupplyDemand> rows = byCommodity.get(c);
			if (rows == null || rows.isEmpty()) continue;
			repo.deleteByCommodity(c);
			repo.saveAll(rows);
			System.out.println("[WASDE] cached " + rows.size() + " rows for " + c);
		}
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

	private Map<String, List<SupplyDemand>> parse(String csv) {
		String[] lines = csv.split("\r?\n");
		if (lines.length < 2) return Map.of();

		List<String> header = parseLine(lines[0]);
		int iComm = col(header, "commodity");
		int iReg  = col(header, "region");
		int iAttr = col(header, "attribute");
		int iVal  = col(header, "value");
		int iUnit = col(header, "unit");
		int iYear = col(header, "marketyear", "year");
		int iDate = col(header, "reportdate", "releasedate");
		if (iComm < 0 || iReg < 0 || iAttr < 0 || iVal < 0 || iYear < 0) {
			System.err.println("[WASDE] header missing required columns: " + header);
			return Map.of();
		}

		Map<String, List<SupplyDemand>> out = new LinkedHashMap<>();
		// per (commodity|region) → attribute → seq, so attribute order is stable
		Map<String, Integer> seqByAttr = new HashMap<>();

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
			String key = commodity + "|" + region + "|" + attribute;
			int seq = seqByAttr.computeIfAbsent(key, k -> seqByAttr.size());

			SupplyDemand sd = new SupplyDemand();
			sd.setCommodity(commodity);
			sd.setRegion(region);
			sd.setMarketYear(year);
			sd.setAttribute(attribute);
			sd.setValue(value);
			sd.setUnit(iUnit >= 0 ? get(f, iUnit).trim() : "");
			sd.setReportDate(iDate >= 0 ? get(f, iDate).trim() : null);
			sd.setSeq(seq);
			out.computeIfAbsent(commodity, k -> new ArrayList<>()).add(sd);
		}
		return out;
	}

	/* ── Read for the API ───────────────────────────────────────────────── */

	public Map<String, Object> getBalanceSheet(String commodity) {
		commodity = commodity.toUpperCase();
		List<SupplyDemand> rows = repo.findByCommodityOrderByRegionAscSeqAsc(commodity);

		Map<String, Object> out = new LinkedHashMap<>();
		out.put("commodity", commodity);
		if (rows.isEmpty()) {
			out.put("years", List.of());
			out.put("us", List.of());
			out.put("world", List.of());
			out.put("message", "WASDE data isn't loaded yet.");
			return out;
		}

		// Latest up-to-3 marketing years (newest first).
		TreeSet<Integer> ys = new TreeSet<>(java.util.Collections.reverseOrder());
		String reportDate = null;
		LocalDateTime updated = null;
		for (SupplyDemand r : rows) {
			ys.add(r.getMarketYear());
			if (reportDate == null && r.getReportDate() != null) reportDate = r.getReportDate();
			if (r.getUpdatedAt() != null && (updated == null || r.getUpdatedAt().isAfter(updated))) updated = r.getUpdatedAt();
		}
		List<Integer> years = new ArrayList<>(ys).subList(0, Math.min(3, ys.size()));

		out.put("years", years);
		out.put("reportDate", reportDate);
		out.put("updatedAt", updated == null ? null : updated.toString());
		out.put("us", buildRegion(rows, "US", years));
		out.put("world", buildRegion(rows, "WORLD", years));
		return out;
	}

	private List<Map<String, Object>> buildRegion(List<SupplyDemand> rows, String region, List<Integer> years) {
		Map<String, Map<String, Object>> byAttr = new LinkedHashMap<>();
		for (SupplyDemand r : rows) {
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
		if (n.contains("soybean") && !n.contains("meal") && !n.contains("oil")) return "SOYBEANS";
		return null;
	}

	private static String mapRegion(String r) {
		String n = norm(r);
		if (n.equals("unitedstates") || n.equals("us")) return "US";
		if (n.equals("world")) return "WORLD";
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

	/** True for metric units (1000 MT, Million Metric Tons, MT/HA, Hectares). */
	private static boolean isMetric(String unit) {
		String n = norm(unit);
		return n.contains("ton") || n.contains("hectare") || n.contains("mt");
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
