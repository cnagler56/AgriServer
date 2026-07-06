package com.home.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;

/**
 * Generates a short, plain-English recap of a USDA Crop Production report using
 * Claude. The model is fed ONLY the figures already computed by
 * {@link UsdaReportsService#getReportSummary}, so the write-up is grounded in
 * real numbers rather than invented facts.
 *
 * Results are cached per (commodity, year, report period): a report's figures
 * don't change once published, so we generate the commentary once and serve it
 * from memory until the next report shifts the period. Fails soft — if the API
 * key isn't configured or the call errors, the summary page just omits the blurb.
 */
@Service
public class CropCommentaryService {

	private static final String SYSTEM = """
		You are an agricultural market analyst writing for farmers on an ag-data website.
		Given figures from a USDA Crop Production report, write a concise, factual recap of
		2-3 sentences (roughly 60-90 words). Rules:
		- Use ONLY the numbers provided. Do not invent data, prices, causes, or forecasts.
		- Lead with the national yield and how it moved vs the previous report and vs last year.
		- Mention one or two notable state movers if any are given.
		- Plain, direct language. No headings, no markdown, no bullet lists, no disclaimers.
		""";

	private final UsdaReportsService reports;
	private final String apiKey;
	private volatile AnthropicClient client;                 // built lazily on first use

	/** key: COMMODITY|year|latestPeriod → generated commentary. */
	private final Map<String, Commentary> cache = new ConcurrentHashMap<>();

	public CropCommentaryService(UsdaReportsService reports,
			@Value("${anthropic.api.key:}") String apiKey) {
		this.reports = reports;
		this.apiKey = apiKey == null ? "" : apiKey.trim();
	}

	/** The blurb plus metadata. {@code available=false} means the UI should hide it. */
	public record Commentary(String commentary, String model, String generatedAt, boolean available) {}

	public Commentary getCommentary(String commodity, Integer year) {
		Map<String, Object> summary = reports.getReportSummary(commodity, year);
		if (summary.get("national") == null) {
			return new Commentary(strOr(summary.get("message"), "No report data to summarize yet."),
				null, null, false);
		}
		if (apiKey.isEmpty()) {
			return new Commentary("AI commentary isn't configured.", null, null, false);
		}

		String cacheKey = commodity.toUpperCase() + "|" + summary.get("year") + "|" + summary.get("latestPeriod");
		Commentary cached = cache.get(cacheKey);
		if (cached != null) return cached;

		try {
			MessageCreateParams params = MessageCreateParams.builder()
				.model("claude-opus-4-8")
				.maxTokens(400L)
				.outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.LOW).build())
				.system(SYSTEM)
				.addUserMessage(buildPrompt(commodity, summary))
				.build();

			Message resp = client().messages().create(params);
			String text = resp.content().stream()
				.flatMap(block -> block.text().stream())
				.map(t -> t.text())
				.reduce("", (a, b) -> a + b)
				.trim();

			if (text.isEmpty()) {
				return new Commentary("AI commentary is unavailable right now.", null, null, false);
			}
			Commentary out = new Commentary(text, "claude-opus-4-8", LocalDateTime.now().toString(), true);
			cache.put(cacheKey, out);
			return out;
		} catch (Exception e) {
			System.err.println("[COMMENTARY] generation failed: "
				+ e.getClass().getSimpleName() + " - " + e.getMessage());
			return new Commentary("AI commentary is unavailable right now.", null, null, false);
		}
	}

	private AnthropicClient client() {
		AnthropicClient c = client;
		if (c == null) {
			synchronized (this) {
				c = client;
				if (c == null) {
					c = AnthropicOkHttpClient.builder().apiKey(apiKey).build();
					client = c;
				}
			}
		}
		return c;
	}

	@SuppressWarnings("unchecked")
	private String buildPrompt(String commodity, Map<String, Object> s) {
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

	@SuppressWarnings("unchecked")
	private void appendMetric(StringBuilder sb, String label, Object metricObj, double div, int dec, String unit) {
		if (!(metricObj instanceof Map)) return;
		Map<String, Object> m = (Map<String, Object>) metricObj;
		sb.append(label).append(" (").append(unit).append("): latest=").append(fmt(m.get("latest"), div, dec))
			.append(", vs previous report=").append(fmt(m.get("momChange"), div, dec))
			.append(", vs last year=").append(fmt(m.get("yoyChange"), div, dec)).append("\n");
	}

	private void appendMovers(StringBuilder sb, String label, List<Map<String, Object>> rows) {
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
