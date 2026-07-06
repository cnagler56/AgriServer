package com.home.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.home.ai.CropCommentaryPrompt;

/**
 * Generates a short, plain-English recap of a USDA Crop Production report using
 * Claude. The model is fed ONLY the figures already computed by
 * {@link UsdaReportsService#getReportSummary}, so the write-up is grounded in
 * real numbers rather than invented facts.
 *
 * Prompt wording lives in {@link CropCommentaryPrompt}; this service is just the
 * orchestration — cache, API call, and fail-soft handling.
 *
 * Results are cached per (commodity, year, report period): a report's figures
 * don't change once published, so we generate the commentary once and serve it
 * from memory until the next report shifts the period. Fails soft — if the API
 * key isn't configured or the call errors, the summary page just omits the blurb.
 */
@Service
public class CropCommentaryService {

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
			Object msg = summary.get("message");
			String fallback = (msg == null || msg.toString().isBlank())
				? "No report data to summarize yet." : msg.toString();
			return new Commentary(fallback, null, null, false);
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
				.system(CropCommentaryPrompt.SYSTEM)
				.addUserMessage(CropCommentaryPrompt.user(commodity, summary))
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
}
