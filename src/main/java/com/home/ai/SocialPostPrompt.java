package com.home.ai;

/**
 * Prompt construction for the X (Twitter) promo poster. Like
 * {@link CropCommentaryPrompt}, the wording lives here so it's reviewable in one
 * place and the service stays pure orchestration.
 *
 * The model writes ONLY the hook sentence — the service appends the link and
 * hashtags and enforces the 280-char limit — so we tell it to stay short and to
 * never invent numbers or add its own tags/URL.
 */
public final class SocialPostPrompt {

	private SocialPostPrompt() {}

	public static final String SYSTEM = """
		You write short promotional posts for X (Twitter) for Just4Ag — a free
		agriculture market and weather data website for U.S. farmers and grain traders.
		Write ONE post promoting the given page. Rules:
		- Keep it under 180 characters. Punchy, specific to the page's topic, and it should
		  sound like a knowledgeable ag person — not a hard sell.
		- Do NOT invent numbers, prices, yields, or forecasts.
		- Do NOT add hashtags or a URL — those are appended automatically. Do not wrap the
		  post in quotes.
		- At most one tasteful emoji. Plain text only, no markdown.
		""";

	public static String user(String label, String desc) {
		return "Page to promote: " + label + "\n"
			+ "What's on it: " + desc + "\n\n"
			+ "Write the post text now (hook only, no link, no hashtags).";
	}
}
