package com.home.ai;

import java.util.List;
import java.util.Map;

/**
 * Prompt construction for the Crop Production AI recap.
 *
 * Kept separate from {@code CropCommentaryService} so the wording — the system
 * instructions and how the report figures are laid out for the model — lives in
 * one reviewable place. As more AI features are added, each gets its own prompt
 * class here under {@code com.home.ai}, and the services stay pure orchestration.
 */
public final class CropCommentaryPrompt {

	private CropCommentaryPrompt() {}

	/** The model's role and the hard rules that keep the recap grounded. */
	public static final String SYSTEM = """
		You are an agricultural market analyst writing for farmers on an ag-data website.
		Given figures from a USDA Crop Production report, write a concise, factual recap of
		2-3 sentences (roughly 60-90 words). Rules:
		- Use ONLY the numbers provided. Do not invent data, prices, causes, or forecasts.
		- Lead with the national yield and how it moved vs the previous report and vs last year.
		- Mention one or two notable state movers if any are given.
		- Plain, direct language. No headings, no markdown, no bullet lists, no disclaimers.
		""";

	/** Assemble the user message from the report summary figures. */
	@SuppressWarnings("unchecked")
	public static String user(String commodity, Map<String, Object> s) {
		StringBuilder sb = new StringBuilder();
		sb.append(commodity.toUpperCase()).append(" — USDA Crop Production, ")
			.append(s.get("latestPeriod")).append(" ").append(s.get("year")).append("\n");
		sb.append("Previous report period: ").append(strOr(s.get("previousPeriod"), "none")).append("\n");
		sb.append("Prior year: ").append(s.get("priorYear")).append("\n");
		sb.append("States counted: ").append(s.get("stateCount")).append("\n\n");

		Map<String, Object> nat = (Map<String, Object>) s.get("national");
		appendMetric(sb, "National yield", nat.get("yield"), 1, 1, "bu/acre");
		appendMetric(sb, "Production",     nat.get("production"), 1e9, 2, "billion bu");
		appendMetric(sb, "Harvested acres", nat.get("acres"), 1e6, 1, "million acres");

		appendMovers(sb, "Biggest yield gainers " + strOr(s.get("moverBasis"), ""),
			(List<Map<String, Object>>) s.get("topGainers"));
		appendMovers(sb, "Biggest yield decliners " + strOr(s.get("moverBasis"), ""),
			(List<Map<String, Object>>) s.get("topDecliners"));
		return sb.toString();
	}

	/* ── helpers ────────────────────────────────────────────────────────── */

	@SuppressWarnings("unchecked")
	private static void appendMetric(StringBuilder sb, String label, Object metricObj,
			double div, int dec, String unit) {
		if (!(metricObj instanceof Map)) return;
		Map<String, Object> m = (Map<String, Object>) metricObj;
		sb.append(label).append(" (").append(unit).append("): latest=").append(fmt(m.get("latest"), div, dec))
			.append(", vs previous report=").append(fmt(m.get("momChange"), div, dec))
			.append(", vs last year=").append(fmt(m.get("yoyChange"), div, dec)).append("\n");
	}

	private static void appendMovers(StringBuilder sb, String label, List<Map<String, Object>> rows) {
		if (rows == null || rows.isEmpty()) return;
		sb.append(label).append(": ");
		for (Map<String, Object> r : rows) {
			sb.append(r.get("state")).append(" ").append(r.get("change")).append(" bu; ");
		}
		sb.append("\n");
	}

	private static String fmt(Object num, double div, int dec) {
		if (!(num instanceof Number)) return "n/a";
		return String.format("%." + dec + "f", ((Number) num).doubleValue() / div);
	}

	private static String strOr(Object v, String dflt) {
		return v == null || v.toString().isBlank() ? dflt : v.toString();
	}
}
