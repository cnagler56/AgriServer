package com.home.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Pattern;

import com.home.Domain.ConabSnapshot;
import com.home.Repository.ConabSnapshotRepository;

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
 * Brazil crop production from CONAB's "Série Histórica de Grãos" — a free,
 * keyless semicolon file with planted area / production / yield by crop year,
 * crop season (1ª/2ª/3ª safra), state, and product. The standout vs. WASDE's
 * single annual Brazil number is the safrinha (2nd-crop corn) split.
 *
 * Columns: ano_agricola;dsc_safra_previsao;uf;produto;id_produto;
 *          area_plantada_mil_ha;producao_mil_t;produtividade
 * Numbers use '.' as the decimal point, no thousands separator.
 */
@Service
public class ConabService {

	private static final String URL =
		"https://portaldeinformacoes.conab.gov.br/downloads/arquivos/SerieHistoricaGraos.txt";
	private static final Pattern CROP_YEAR = Pattern.compile("\\d{4}/\\d{2}");

	/** Site commodity code → CONAB produto. */
	private static final Map<String, String> PRODUTO = Map.of(
		"CORN", "MILHO", "SOYBEANS", "SOJA", "WHEAT", "TRIGO");

	private final RestTemplate restTemplate;
	private final ConabSnapshotRepository snapshotRepo;
	private volatile List<Row> rows = List.of();
	private volatile LocalDateTime updatedAt;

	public ConabService(RestTemplate restTemplate, ConabSnapshotRepository snapshotRepo) {
		this.restTemplate = restTemplate;
		this.snapshotRepo = snapshotRepo;
	}

	/** One CONAB observation (thousand ha / thousand tonnes). */
	private record Row(String cropYear, String season, String uf, String produto, double area, double prod) {}

	/* ── scheduling ─────────────────────────────────────────────────────── */

	@EventListener(ApplicationReadyEvent.class)
	public void prewarm() {
		new Thread(() -> {
			try { refresh(); } catch (Exception e) {
				System.err.println("[CONAB] startup load failed: " + e.getMessage());
			}
		}, "conab-prewarm").start();
	}

	/** CONAB updates monthly; a daily fetch keeps us current. */
	@Scheduled(cron = "0 45 6 * * *", zone = "America/Chicago")
	public void scheduledRefresh() {
		try { refresh(); } catch (Exception e) {
			System.err.println("[CONAB] scheduled load failed: " + e.getMessage());
		}
	}

	/* ── load + parse ───────────────────────────────────────────────────── */

	public void refresh() {
		String text = fetch();
		if (text == null) { System.err.println("[CONAB] no data"); return; }
		List<Row> parsed = parse(text);
		if (parsed.isEmpty()) { System.err.println("[CONAB] parsed 0 rows"); return; }
		rows = parsed;
		updatedAt = LocalDateTime.now();
		System.out.println("[CONAB] cached " + parsed.size() + " rows");
		snapshotNational(parsed);
	}

	/** Record this month's national production per crop so we can show MoM change later. */
	private void snapshotNational(List<Row> parsed) {
		int monthKey = currentMonthKey();
		for (String commodity : PRODUTO.keySet()) {
			try {
				String produto = PRODUTO.get(commodity);
				List<Row> crop = parsed.stream().filter(r -> produto.equals(r.produto())).toList();
				String latest = crop.stream().map(Row::cropYear).filter(y -> CROP_YEAR.matcher(y).matches())
					.max(String::compareTo).orElse(null);
				if (latest == null) continue;
				final String cy = latest;
				double prod = crop.stream().filter(r -> cy.equals(r.cropYear())).mapToDouble(Row::prod).sum();
				if (prod <= 0) continue;
				ConabSnapshot snap = snapshotRepo
					.findFirstByCommodityAndCropYearAndMonthKey(commodity, cy, monthKey)
					.orElseGet(ConabSnapshot::new);
				snap.setCommodity(commodity);
				snap.setCropYear(cy);
				snap.setMonthKey(monthKey);
				snap.setProduction(prod);
				snapshotRepo.save(snap);
			} catch (Exception e) {
				System.err.println("[CONAB] snapshot " + commodity + " failed: " + e.getMessage());
			}
		}
	}

	private static int currentMonthKey() {
		YearMonth ym = YearMonth.now();
		return ym.getYear() * 100 + ym.getMonthValue();
	}

	private String fetch() {
		try {
			HttpHeaders h = new HttpHeaders();
			h.set("User-Agent", "just4ag/1.0 (CONAB)");
			ResponseEntity<byte[]> resp = restTemplate.exchange(URL, HttpMethod.GET, new HttpEntity<>(h), byte[].class);
			if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null)
				return new String(resp.getBody(), StandardCharsets.ISO_8859_1);
			System.err.println("[CONAB] HTTP " + resp.getStatusCode());
			return null;
		} catch (Exception e) {
			System.err.println("[CONAB] fetch failed: " + e.getClass().getSimpleName() + " " + e.getMessage());
			return null;
		}
	}

	private List<Row> parse(String text) {
		List<Row> out = new ArrayList<>();
		String[] lines = text.split("\r?\n");
		for (int i = 1; i < lines.length; i++) {           // skip header
			if (lines[i].isBlank()) continue;
			String[] f = lines[i].split(";");
			if (f.length < 7) continue;
			out.add(new Row(f[0].trim(), f[1].trim(), f[2].trim(), f[3].trim(),
				num(f[5]), num(f[6])));
		}
		return out;
	}

	/* ── read for the API ───────────────────────────────────────────────── */

	public Map<String, Object> getProduction(String commodity) {
		Map<String, Object> out = new LinkedHashMap<>();
		out.put("commodity", commodity);
		out.put("source", "CONAB — Série Histórica de Grãos");
		out.put("updatedAt", updatedAt == null ? null : updatedAt.toString());
		out.put("unit", "thousand tonnes");

		String produto = PRODUTO.get(commodity.toUpperCase());
		if (produto == null) { out.put("message", "Not tracked by CONAB."); return out; }

		List<Row> crop = rows.stream().filter(r -> produto.equals(r.produto())).toList();
		String latest = crop.stream().map(Row::cropYear).filter(y -> CROP_YEAR.matcher(y).matches())
			.max(String::compareTo).orElse(null);
		if (latest == null) { out.put("message", "No CONAB data loaded yet."); return out; }
		String prior = priorYear(latest);

		out.put("cropYear", latest);
		out.put("priorCropYear", prior);

		List<Row> cur = crop.stream().filter(r -> latest.equals(r.cropYear())).toList();
		double prod = cur.stream().mapToDouble(Row::prod).sum();
		double area = cur.stream().mapToDouble(Row::area).sum();
		out.put("production", round(prod));
		out.put("area", round(area));
		out.put("yieldTha", area > 0 ? round(prod / area * 10) / 10.0 : null);

		double priorProd = crop.stream().filter(r -> r.cropYear().equals(prior)).mapToDouble(Row::prod).sum();
		if (priorProd > 0) out.put("productionYoYPct", Math.round((prod - priorProd) * 1000.0 / priorProd) / 10.0);

		// Month-over-month vs our most recent earlier snapshot for this crop year.
		ConabSnapshot lastMonth = snapshotRepo
			.findFirstByCommodityAndCropYearAndMonthKeyLessThanOrderByMonthKeyDesc(
				commodity.toUpperCase(), latest, currentMonthKey());
		if (lastMonth != null && lastMonth.getProduction() != null && lastMonth.getProduction() > 0) {
			out.put("productionMoM", round(prod - lastMonth.getProduction()));
			out.put("productionMoMPct", Math.round((prod - lastMonth.getProduction()) * 1000.0 / lastMonth.getProduction()) / 10.0);
			out.put("momMonthKey", lastMonth.getMonthKey());
		}

		// Season split (1ª / 2ª / 3ª safra), ordered.
		Map<String, Double> seasonMap = new TreeMap<>();
		for (Row r : cur) seasonMap.merge(r.season(), r.prod(), Double::sum);
		List<Map<String, Object>> seasons = new ArrayList<>();
		seasonMap.forEach((k, v) -> { if (v > 0) seasons.add(seasonEntry(k, v)); });
		out.put("seasons", seasons);

		// Top 5 states by production, each with its production-weighted yield.
		Map<String, Double> prodByState = new LinkedHashMap<>();
		Map<String, Double> areaByState = new LinkedHashMap<>();
		for (Row r : cur) {
			prodByState.merge(r.uf(), r.prod(), Double::sum);
			areaByState.merge(r.uf(), r.area(), Double::sum);
		}
		List<Map<String, Object>> topStates = prodByState.entrySet().stream()
			.filter(e -> e.getValue() > 0)
			.sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
			.limit(5)
			.map(e -> {
				Map<String, Object> m = new LinkedHashMap<>();
				m.put("state", e.getKey());
				m.put("production", round(e.getValue()));
				Double ar = areaByState.get(e.getKey());
				m.put("yieldTha", ar != null && ar > 0 ? Math.round(e.getValue() / ar * 10) / 10.0 : null);
				return m;
			})
			.toList();
		out.put("topStates", topStates);

		// All states (by UF code, e.g. "MT") for the choropleth map.
		List<Map<String, Object>> allStates = prodByState.entrySet().stream()
			.filter(e -> e.getValue() > 0)
			.sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
			.map(e -> {
				Map<String, Object> m = new LinkedHashMap<>();
				m.put("state", e.getKey());
				m.put("production", round(e.getValue()));
				Double ar = areaByState.get(e.getKey());
				m.put("yieldTha", ar != null && ar > 0 ? Math.round(e.getValue() / ar * 10) / 10.0 : null);
				return m;
			})
			.toList();
		out.put("allStates", allStates);
		return out;
	}

	private static Map<String, Object> seasonEntry(String season, double prod) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("name", label(season));
		m.put("production", round(prod));
		return m;
	}

	/** "2ª SAFRA" → "2nd crop (safrinha)", etc. */
	private static String label(String season) {
		String s = season.toUpperCase();
		if (s.startsWith("1")) return "1st crop";
		if (s.startsWith("2")) return "2nd crop (safrinha)";
		if (s.startsWith("3")) return "3rd crop";
		if (s.contains("UNICA")) return "Single crop";
		return season;
	}

	private static String priorYear(String cropYear) {
		try {
			int y = Integer.parseInt(cropYear.substring(0, 4));
			return (y - 1) + "/" + String.format("%02d", y % 100);
		} catch (Exception e) { return null; }
	}

	private static double num(String s) {
		if (s == null) return 0;
		try { return Double.parseDouble(s.trim()); } catch (Exception e) { return 0; }
	}
	private static long round(double v) { return Math.round(v); }
}
