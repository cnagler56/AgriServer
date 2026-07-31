package com.home.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.home.Domain.SocialPost;
import com.home.Repository.SocialPostRepository;
import com.home.ai.SocialPostPrompt;

/**
 * Auto-promotes Just4Ag on X (Twitter) twice a day — 9:30am and 3:00pm Central.
 *
 * Each run rotates to the next site page, asks Claude for a fresh hook (so the
 * copy isn't a stale template), appends the page link and a rotating set of ag
 * hashtags within X's 280-char limit, and posts via {@link XClient}.
 *
 * Two safety gates keep it from posting prematurely:
 *   - social.posting.enabled (SOCIAL_POSTING_ENABLED) defaults to false.
 *   - if X credentials aren't set, it stays in dry-run regardless.
 * In either case the drafted tweet is logged and stored with status DRYRUN, so
 * you can preview real copy before ever going live.
 */
@Service
public class SocialPostService {

	/** A promotable page: rotation key, path, display label, topic blurb, hashtags. */
	public record Page(String key, String path, String label, String desc, String hashtags) {}

	/** Rotation order. Two posts/day cycles the full list about every four days. */
	public static final List<Page> PAGES = List.of(
		new Page("yield-challenge", "/usda-challenge", "USDA Yield Challenge",
			"An interactive game: tweak state-by-state yields, get a production-weighted national number, and see how you stack up against USDA and the crowd.",
			"#Corn #Soybeans #AgTwitter"),
		new Page("outlook", "/outlook", "Extended Weather Outlook",
			"NOAA's 6-10 and 8-14 day temperature and precipitation outlooks for the Corn Belt, with a per-state warmer/cooler/wetter/drier trend.",
			"#Weather #Agriculture #CropProgress"),
		new Page("corn", "/corn", "Corn Dashboard",
			"Corn front-month futures charts, supply & demand, export sales, and crop progress in one place.",
			"#Corn #Grain #AgTwitter"),
		new Page("vegetation", "/vegetation", "Vegetation Health",
			"County-level crop vegetation health index maps built from NOAA satellite data.",
			"#Agriculture #Farming #Drought"),
		new Page("soybeans", "/soybeans", "Soybeans Dashboard",
			"Soybean futures, supply & demand, export sales, and crop progress for the season.",
			"#Soybeans #Grain #AgTwitter"),
		new Page("usda-reports", "/usda-reports", "USDA Reports",
			"WASDE, Crop Production, and Grain Stocks figures pulled together and made easy to read.",
			"#USDA #WASDE #Grain"),
		new Page("wheat", "/wheat", "Wheat Dashboard",
			"Wheat futures, supply & demand, and crop progress across the classes.",
			"#Wheat #Grain #Harvest25"),
		new Page("crop-progress", "/cropprogress", "Crop Progress",
			"Weekly USDA crop condition and progress ratings by state, season over season.",
			"#CropProgress #Corn #Soybeans")
	);

	private static final int TWEET_LIMIT = 280;

	private final SocialPostRepository repo;
	private final XClient x;
	private final String siteUrl;
	private final boolean enabled;
	private final String apiKey;                       // Anthropic
	private volatile AnthropicClient client;           // built lazily

	public SocialPostService(SocialPostRepository repo, XClient x,
			@Value("${social.site-url:https://just4ag.com}") String siteUrl,
			@Value("${social.posting.enabled:false}") boolean enabled,
			@Value("${anthropic.api.key:}") String apiKey) {
		this.repo = repo;
		this.x = x;
		this.siteUrl = stripTrailingSlash(siteUrl);
		this.enabled = enabled;
		this.apiKey = apiKey == null ? "" : apiKey.trim();
	}

	/* ── schedule: 9:30am & 3:00pm Central ──────────────────────────────── */

	@Scheduled(cron = "0 30 9 * * *", zone = "America/Chicago")
	public void morning() { safeRun("MORNING"); }

	@Scheduled(cron = "0 0 15 * * *", zone = "America/Chicago")
	public void afternoon() { safeRun("AFTERNOON"); }

	private void safeRun(String slot) {
		try { runOnce(slot, null); }
		catch (Exception e) {
			System.err.println("[SOCIAL] " + slot + " run failed: "
				+ e.getClass().getSimpleName() + " - " + e.getMessage());
		}
	}

	/* ── one post ───────────────────────────────────────────────────────── */

	/** Compose (and, if live, publish) one post. {@code forcedKey} overrides rotation. */
	public SocialPost runOnce(String slot, String forcedKey) {
		Page page = forcedKey == null ? nextPage() : findPage(forcedKey);
		String hook = generateHook(page);
		String url = siteUrl + page.path();
		String text = compose(hook, url, page.hashtags());

		SocialPost row = new SocialPost();
		row.setPageKey(page.key());
		row.setPageLabel(page.label());
		row.setUrl(url);
		row.setText(text);
		row.setSlot(slot);
		row.setCreatedAt(LocalDateTime.now());

		if (enabled && x.isConfigured()) {
			try {
				row.setTweetId(x.postTweet(text));
				row.setStatus("POSTED");
				System.out.println("[SOCIAL] posted " + page.key() + " (" + slot + ") id=" + row.getTweetId());
			} catch (Exception e) {
				row.setStatus("FAILED");
				row.setNote(shorten(e.getClass().getSimpleName() + ": " + e.getMessage(), 590));
				System.err.println("[SOCIAL] post failed for " + page.key() + ": " + e.getMessage());
			}
		} else {
			row.setStatus("DRYRUN");
			row.setNote(!enabled
				? "posting disabled (SOCIAL_POSTING_ENABLED=false)"
				: "X credentials not configured");
			System.out.println("[SOCIAL] DRY-RUN " + page.key() + " (" + slot + "):\n" + text);
		}
		return repo.save(row);
	}

	/* ── copy ───────────────────────────────────────────────────────────── */

	private String generateHook(Page p) {
		if (apiKey.isEmpty()) return p.desc();          // fallback: the blurb itself
		try {
			MessageCreateParams params = MessageCreateParams.builder()
				.model("claude-opus-4-8")
				.maxTokens(150L)
				.outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.LOW).build())
				.system(SocialPostPrompt.SYSTEM)
				.addUserMessage(SocialPostPrompt.user(p.label(), p.desc()))
				.build();
			Message resp = client().messages().create(params);
			String text = resp.content().stream()
				.flatMap(b -> b.text().stream())
				.map(t -> t.text())
				.reduce("", (a, b) -> a + b)
				.trim();
			// Strip stray surrounding quotes the model sometimes adds.
			if (text.length() >= 2 && text.startsWith("\"") && text.endsWith("\"")) {
				text = text.substring(1, text.length() - 1).trim();
			}
			return text.isEmpty() ? p.desc() : text;
		} catch (Exception e) {
			System.err.println("[SOCIAL] hook generation failed: " + e.getMessage());
			return p.desc();
		}
	}

	/** hook + link + hashtags, trimmed so the whole tweet fits in 280 chars. */
	private String compose(String hook, String url, String hashtags) {
		int budget = TWEET_LIMIT - url.length() - hashtags.length() - 3; // 2 spaces + newline
		String h = hook.trim();
		if (h.length() > budget && budget > 1) h = h.substring(0, budget - 1).trim() + "…";
		return h + "\n" + url + " " + hashtags;
	}

	/* ── rotation ───────────────────────────────────────────────────────── */

	private Page nextPage() {
		return repo.findTopByStatusInOrderByCreatedAtDesc(List.of("POSTED", "DRYRUN"))
			.map(last -> PAGES.get((indexOfKey(last.getPageKey()) + 1) % PAGES.size()))
			.orElse(PAGES.get(0));
	}

	private int indexOfKey(String key) {
		for (int i = 0; i < PAGES.size(); i++) if (PAGES.get(i).key().equals(key)) return i;
		return -1;  // unknown/removed key → next becomes PAGES[0]
	}

	private Page findPage(String key) {
		return PAGES.stream().filter(p -> p.key().equals(key)).findFirst()
			.orElseThrow(() -> new IllegalArgumentException("Unknown page: " + key));
	}

	/* ── admin-facing helpers ───────────────────────────────────────────── */

	/** Compose without posting or saving — for the admin preview. */
	public Map<String, Object> preview(String forcedKey) {
		Page page = forcedKey == null ? nextPage() : findPage(forcedKey);
		String url = siteUrl + page.path();
		String text = compose(generateHook(page), url, page.hashtags());
		Map<String, Object> out = new LinkedHashMap<>();
		out.put("pageKey", page.key());
		out.put("pageLabel", page.label());
		out.put("url", url);
		out.put("text", text);
		out.put("length", text.length());
		out.put("aiUsed", !apiKey.isEmpty());
		return out;
	}

	public Map<String, Object> status() {
		Map<String, Object> out = new LinkedHashMap<>();
		out.put("enabled", enabled);
		out.put("xConfigured", x.isConfigured());
		out.put("aiConfigured", !apiKey.isEmpty());
		out.put("siteUrl", siteUrl);
		out.put("schedule", "9:30am & 3:00pm America/Chicago");
		List<Map<String, String>> pages = new ArrayList<>();
		for (Page p : PAGES) pages.add(Map.of("key", p.key(), "label", p.label(), "path", p.path()));
		out.put("pages", pages);
		return out;
	}

	public List<SocialPost> recent() { return repo.findTop20ByOrderByCreatedAtDesc(); }

	/** Non-destructive check of the X credentials (GET /2/users/me). Sends no tweet. */
	public Map<String, Object> verify() {
		Map<String, Object> out = new LinkedHashMap<>();
		out.put("configured", x.isConfigured());
		if (!x.isConfigured()) {
			out.put("ok", false);
			out.put("error", "X API credentials are not set on the server.");
			return out;
		}
		try {
			String handle = x.verifyHandle();
			out.put("ok", true);
			out.put("handle", handle);
		} catch (Exception e) {
			out.put("ok", false);
			out.put("error", e.getMessage());
		}
		return out;
	}

	/* ── infra ──────────────────────────────────────────────────────────── */

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

	private static String stripTrailingSlash(String s) {
		String v = s == null ? "" : s.trim();
		return v.endsWith("/") ? v.substring(0, v.length() - 1) : v;
	}

	private static String shorten(String s, int max) {
		if (s == null) return null;
		return s.length() <= max ? s : s.substring(0, max);
	}
}
