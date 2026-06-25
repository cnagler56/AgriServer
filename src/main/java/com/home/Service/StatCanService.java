package com.home.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

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
 * Canada crop production from Statistics Canada — table 32-10-0359 ("Estimated
 * areas, yield, production… of principal field crops"). Free, keyless bulk CSV
 * (zipped). Covers canola (Canada's #1 crop, absent from WASDE), wheat, corn,
 * and soybeans, by province and nationally, with area / production / yield.
 */
@Service
public class StatCanService {

	private static final String ZIP_URL = "https://www150.statcan.gc.ca/n1/tbl/csv/32100359-eng.zip";

	/** Site commodity code → StatCan "Type of crop". */
	private static final Map<String, String> CROP = Map.of(
		"CANOLA", "Canola (rapeseed)",
		"WHEAT", "Wheat, all",
		"CORN", "Corn for grain",
		"SOYBEANS", "Soybeans");

	private static final String D_PROD = "Production (metric tonnes)";
	private static final String D_YIELD = "Average yield (kilograms per hectare)";
	private static final String D_AREA = "Harvested area (hectares)";
	private static final Set<String> DISPOSITIONS = Set.of(D_PROD, D_YIELD, D_AREA);

	private final RestTemplate restTemplate;
	private volatile List<Row> rows = List.of();
	private volatile LocalDateTime updatedAt;

	public StatCanService(RestTemplate restTemplate) {
		this.restTemplate = restTemplate;
	}

	/** One observation: value is in the disposition's native unit (tonnes / kg-per-ha / ha). */
	private record Row(int year, String geo, String crop, String disp, double value) {}

	/* ── scheduling ─────────────────────────────────────────────────────── */

	@EventListener(ApplicationReadyEvent.class)
	public void prewarm() {
		new Thread(() -> {
			try { refresh(); } catch (Exception e) {
				System.err.println("[STATCAN] startup load failed: " + e.getMessage());
			}
		}, "statcan-prewarm").start();
	}

	/** StatCan updates this table a few times a year; a daily fetch is plenty. */
	@Scheduled(cron = "0 50 6 * * *", zone = "America/Chicago")
	public void scheduledRefresh() {
		try { refresh(); } catch (Exception e) {
			System.err.println("[STATCAN] scheduled load failed: " + e.getMessage());
		}
	}

	/* ── load + parse ───────────────────────────────────────────────────── */

	public void refresh() {
		try {
			HttpHeaders h = new HttpHeaders();
			h.set("User-Agent", "just4ag/1.0 (StatCan)");
			ResponseEntity<byte[]> resp = restTemplate.exchange(ZIP_URL, HttpMethod.GET, new HttpEntity<>(h), byte[].class);
			if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
				System.err.println("[STATCAN] HTTP " + resp.getStatusCode());
				return;
			}
			List<Row> parsed = parseZip(resp.getBody());
			if (parsed.isEmpty()) { System.err.println("[STATCAN] parsed 0 rows"); return; }
			rows = parsed;
			updatedAt = LocalDateTime.now();
			System.out.println("[STATCAN] cached " + parsed.size() + " field-crop rows");
		} catch (Exception e) {
			System.err.println("[STATCAN] fetch/parse failed: " + e.getClass().getSimpleName() + " " + e.getMessage());
		}
	}

	private List<Row> parseZip(byte[] zipBytes) throws Exception {
		List<Row> out = new ArrayList<>();
		Set<String> crops = Set.copyOf(CROP.values());
		try (ZipInputStream zis = new ZipInputStream(new java.io.ByteArrayInputStream(zipBytes))) {
			ZipEntry entry;
			while ((entry = zis.getNextEntry()) != null) {
				String name = entry.getName().toLowerCase();
				if (!name.endsWith(".csv") || name.contains("metadata")) continue;
				BufferedReader br = new BufferedReader(new InputStreamReader(zis, StandardCharsets.UTF_8));
				String headerLine = br.readLine();
				if (headerLine == null) break;
				List<String> header = parseLine(headerLine);
				int iYear = col(header, "REF_DATE"), iGeo = col(header, "GEO"),
					iDisp = col(header, "Harvest disposition"), iCrop = col(header, "Type of crop"),
					iVal = col(header, "VALUE"), iScalar = col(header, "SCALAR_ID");
				String line;
				while ((line = br.readLine()) != null) {
					if (line.isBlank()) continue;
					List<String> f = parseLine(line);
					if (f.size() <= iVal) continue;
					String crop = get(f, iCrop);
					if (!crops.contains(crop)) continue;
					String disp = get(f, iDisp);
					if (!DISPOSITIONS.contains(disp)) continue;
					Integer year = parseInt(get(f, iYear));
					Double val = parseNum(get(f, iVal));
					if (year == null || val == null) continue;
					int scalar = iScalar >= 0 ? (parseInt(get(f, iScalar)) == null ? 0 : parseInt(get(f, iScalar))) : 0;
					out.add(new Row(year, get(f, iGeo), crop, disp, val * Math.pow(10, scalar)));
				}
				break; // only the data CSV
			}
		}
		return out;
	}

	/* ── read for the API ───────────────────────────────────────────────── */

	public Map<String, Object> getProduction(String commodity) {
		Map<String, Object> out = new LinkedHashMap<>();
		out.put("commodity", commodity);
		out.put("source", "Statistics Canada — Table 32-10-0359");
		out.put("updatedAt", updatedAt == null ? null : updatedAt.toString());
		out.put("unit", "thousand tonnes");

		String crop = CROP.get(commodity.toUpperCase());
		if (crop == null) { out.put("message", "Not tracked."); return out; }

		List<Row> prod = rows.stream().filter(r -> r.crop().equals(crop) && r.disp().equals(D_PROD)).toList();
		Integer latest = prod.stream().filter(r -> "Canada".equals(r.geo())).map(Row::year).max(Integer::compareTo).orElse(null);
		if (latest == null) { out.put("message", "No Canada data loaded yet."); return out; }
		out.put("cropYear", String.valueOf(latest));
		out.put("priorCropYear", String.valueOf(latest - 1));

		// thousand tonnes / thousand hectares / t per ha, to match the CONAB-style panel.
		double natlProd = sum(prod, "Canada", latest) / 1000.0;
		out.put("production", Math.round(natlProd));
		out.put("area", Math.round(value(rows, crop, D_AREA, "Canada", latest) / 1000.0));
		Double yieldKg = valueOrNull(rows, crop, D_YIELD, "Canada", latest);
		out.put("yieldTha", yieldKg == null ? null : Math.round(yieldKg / 100.0) / 10.0);

		double priorProd = sum(prod, "Canada", latest - 1) / 1000.0;
		if (priorProd > 0) out.put("productionYoYPct", Math.round((natlProd - priorProd) * 1000.0 / priorProd) / 10.0);

		// Top provinces (exclude national + regional aggregates).
		final int yr = latest;
		List<Map<String, Object>> topStates = prod.stream()
			.filter(r -> r.year() == yr && !"Canada".equals(r.geo()) && !r.geo().toLowerCase().contains("provinces"))
			.sorted((a, b) -> Double.compare(b.value(), a.value()))
			.limit(5)
			.map(r -> {
				Map<String, Object> m = new LinkedHashMap<>();
				m.put("state", r.geo());
				m.put("production", Math.round(r.value() / 1000.0));
				Double yk = valueOrNull(rows, crop, D_YIELD, r.geo(), yr);
				m.put("yieldTha", yk == null ? null : Math.round(yk / 100.0) / 10.0);
				return m;
			})
			.toList();
		out.put("topStates", topStates);
		return out;
	}

	private static double sum(List<Row> rows, String geo, int year) {
		return rows.stream().filter(r -> r.year() == year && geo.equals(r.geo())).mapToDouble(Row::value).sum();
	}
	private static double value(List<Row> rows, String crop, String disp, String geo, int year) {
		Double v = valueOrNull(rows, crop, disp, geo, year);
		return v == null ? 0 : v;
	}
	private static Double valueOrNull(List<Row> rows, String crop, String disp, String geo, int year) {
		return rows.stream().filter(r -> r.year() == year && crop.equals(r.crop()) && disp.equals(r.disp()) && geo.equals(r.geo()))
			.map(Row::value).findFirst().orElse(null);
	}

	/* ── CSV helpers ────────────────────────────────────────────────────── */

	private static int col(List<String> header, String key) {
		for (int i = 0; i < header.size(); i++)
			// strip a leading BOM / non-letter junk before matching (e.g. "﻿REF_DATE")
			if (header.get(i).replaceFirst("^[^A-Za-z]+", "").trim().equalsIgnoreCase(key)) return i;
		return -1;
	}
	private static String get(List<String> f, int i) { return (i >= 0 && i < f.size()) ? f.get(i).trim() : ""; }
	private static Integer parseInt(String s) {
		try { return Integer.parseInt(s.replaceFirst("^[^0-9-]+", "").trim()); } catch (Exception e) { return null; }
	}
	private static Double parseNum(String s) {
		String t = s.trim();
		if (t.isEmpty()) return null;
		try { return Double.parseDouble(t); } catch (Exception e) { return null; }
	}
	private static List<String> parseLine(String line) {
		List<String> out = new ArrayList<>();
		StringBuilder cur = new StringBuilder();
		boolean inQ = false;
		for (int i = 0; i < line.length(); i++) {
			char c = line.charAt(i);
			if (inQ) {
				if (c == '"') { if (i + 1 < line.length() && line.charAt(i + 1) == '"') { cur.append('"'); i++; } else inQ = false; }
				else cur.append(c);
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
