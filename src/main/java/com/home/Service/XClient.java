package com.home.Service;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Thin X (Twitter) API v2 client for publishing a tweet, signed with OAuth 1.0a
 * User Context (the auth POST /2/tweets requires). All four secrets come from
 * environment/config — never hardcoded:
 *
 *   x.api.key      ← X_API_KEY        (consumer/API key)
 *   x.api.secret   ← X_API_SECRET     (consumer/API secret)
 *   x.access.token ← X_ACCESS_TOKEN   (the site account's access token)
 *   x.access.secret← X_ACCESS_SECRET  (access token secret)
 *
 * If any is missing, {@link #isConfigured()} is false and the poster stays in
 * dry-run mode.
 */
@Component
public class XClient {

	private static final String TWEETS_URL = "https://api.x.com/2/tweets";
	private static final String USERS_ME_URL = "https://api.x.com/2/users/me";

	private final RestTemplate rt;
	private final ObjectMapper mapper = new ObjectMapper();
	private final SecureRandom rng = new SecureRandom();

	private final String apiKey;
	private final String apiSecret;
	private final String token;
	private final String tokenSecret;

	public XClient(RestTemplate rt,
			@Value("${x.api.key:}") String apiKey,
			@Value("${x.api.secret:}") String apiSecret,
			@Value("${x.access.token:}") String token,
			@Value("${x.access.secret:}") String tokenSecret) {
		this.rt = rt;
		this.apiKey = trim(apiKey);
		this.apiSecret = trim(apiSecret);
		this.token = trim(token);
		this.tokenSecret = trim(tokenSecret);
	}

	public boolean isConfigured() {
		return !apiKey.isEmpty() && !apiSecret.isEmpty() && !token.isEmpty() && !tokenSecret.isEmpty();
	}

	/** Publishes {@code text} as a tweet and returns the new tweet id. Throws on failure. */
	public String postTweet(String text) throws Exception {
		if (!isConfigured()) throw new IllegalStateException("X API credentials not configured");

		HttpHeaders h = new HttpHeaders();
		h.set(HttpHeaders.AUTHORIZATION, authHeader("POST", TWEETS_URL));
		h.setContentType(MediaType.APPLICATION_JSON);
		String body = mapper.writeValueAsString(Map.of("text", text));

		ResponseEntity<String> resp = rt.exchange(
			TWEETS_URL, HttpMethod.POST, new HttpEntity<>(body, h), String.class);
		JsonNode node = mapper.readTree(resp.getBody() == null ? "{}" : resp.getBody());
		JsonNode id = node.path("data").path("id");
		if (id.isMissingNode()) throw new IllegalStateException("X response had no tweet id: " + resp.getBody());
		return id.asText();
	}

	/**
	 * Non-destructive credential check: calls GET /2/users/me and returns the
	 * authenticated handle. Confirms the keys, the Project enrollment, and
	 * user-context auth all work without posting anything. Throws with X's own
	 * error detail on failure (e.g. 403 client-not-enrolled, 401 bad keys).
	 */
	public String verifyHandle() throws Exception {
		if (!isConfigured()) throw new IllegalStateException("X API credentials not configured");
		HttpHeaders h = new HttpHeaders();
		h.set(HttpHeaders.AUTHORIZATION, authHeader("GET", USERS_ME_URL));
		try {
			ResponseEntity<String> resp = rt.exchange(
				USERS_ME_URL, HttpMethod.GET, new HttpEntity<>(h), String.class);
			JsonNode node = mapper.readTree(resp.getBody() == null ? "{}" : resp.getBody());
			return node.path("data").path("username").asText("");
		} catch (HttpStatusCodeException e) {
			throw new IllegalStateException(
				e.getStatusCode().value() + " — " + extractDetail(e.getResponseBodyAsString()));
		}
	}

	/** Pull the human-readable reason out of X's JSON error body. */
	private String extractDetail(String body) {
		if (body == null || body.isBlank()) return "no response body";
		try {
			JsonNode n = mapper.readTree(body);
			if (n.hasNonNull("detail")) return n.path("detail").asText();
			if (n.hasNonNull("title")) return n.path("title").asText();
			JsonNode errs = n.path("errors");
			if (errs.isArray() && errs.size() > 0) return errs.get(0).path("message").asText(body);
		} catch (Exception ignore) { /* fall through to raw */ }
		return body.length() > 300 ? body.substring(0, 300) : body;
	}

	/* ── OAuth 1.0a signing ─────────────────────────────────────────────── */

	private String authHeader(String method, String url) throws Exception {
		TreeMap<String, String> oauth = new TreeMap<>();
		oauth.put("oauth_consumer_key", apiKey);
		oauth.put("oauth_nonce", nonce());
		oauth.put("oauth_signature_method", "HMAC-SHA1");
		oauth.put("oauth_timestamp", String.valueOf(System.currentTimeMillis() / 1000));
		oauth.put("oauth_token", token);
		oauth.put("oauth_version", "1.0");

		// Parameter string: sorted, percent-encoded, & the JSON body is not signed.
		StringBuilder params = new StringBuilder();
		for (Map.Entry<String, String> e : oauth.entrySet()) {
			if (params.length() > 0) params.append('&');
			params.append(enc(e.getKey())).append('=').append(enc(e.getValue()));
		}
		String base = method.toUpperCase() + "&" + enc(url) + "&" + enc(params.toString());
		String signingKey = enc(apiSecret) + "&" + enc(tokenSecret);
		oauth.put("oauth_signature", hmacSha1(base, signingKey));

		StringBuilder hdr = new StringBuilder("OAuth ");
		boolean first = true;
		for (Map.Entry<String, String> e : oauth.entrySet()) {
			if (!first) hdr.append(", ");
			hdr.append(enc(e.getKey())).append("=\"").append(enc(e.getValue())).append('"');
			first = false;
		}
		return hdr.toString();
	}

	private String nonce() {
		byte[] b = new byte[32];
		rng.nextBytes(b);
		return Base64.getEncoder().encodeToString(b).replaceAll("[^A-Za-z0-9]", "");
	}

	private static String hmacSha1(String data, String key) throws Exception {
		Mac mac = Mac.getInstance("HmacSHA1");
		mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
		return Base64.getEncoder().encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
	}

	/** RFC 3986 percent-encoding (OAuth's stricter variant). */
	private static String enc(String s) {
		StringBuilder sb = new StringBuilder();
		for (byte raw : s.getBytes(StandardCharsets.UTF_8)) {
			int c = raw & 0xFF;
			if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
					|| c == '-' || c == '.' || c == '_' || c == '~') {
				sb.append((char) c);
			} else {
				sb.append('%').append(String.format("%02X", c));
			}
		}
		return sb.toString();
	}

	private static String trim(String s) { return s == null ? "" : s.trim(); }
}
