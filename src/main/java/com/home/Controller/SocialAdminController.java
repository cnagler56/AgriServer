package com.home.Controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.home.Domain.Role;
import com.home.Domain.SocialPost;
import com.home.Domain.User;
import com.home.Service.SessionService;
import com.home.Service.SocialPostService;

/**
 * Admin console for the X auto-poster: check status, preview the next post's
 * copy, fire a one-off post now (honoring the enabled flag / dry-run), and read
 * the recent log. All endpoints are ADMIN-only via the session cookie.
 */
@RestController
@CrossOrigin(origins = "http://localhost:3000", allowCredentials = "true")
public class SocialAdminController {

	private final SocialPostService social;
	private final SessionService sessionService;

	public SocialAdminController(SocialPostService social, SessionService sessionService) {
		this.social = social;
		this.sessionService = sessionService;
	}

	@GetMapping("/api/admin/social/status")
	public Map<String, Object> status(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token) {
		requireAdmin(token);
		return social.status();
	}

	@GetMapping("/api/admin/social/log")
	public List<SocialPost> log(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token) {
		requireAdmin(token);
		return social.recent();
	}

	/** Compose the next (or a chosen) post without publishing or saving it. */
	@PostMapping("/api/admin/social/preview")
	public Map<String, Object> preview(
			@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
			@RequestBody(required = false) PageChoice body) {
		requireAdmin(token);
		try {
			return social.preview(body == null ? null : blankToNull(body.pageKey()));
		} catch (IllegalArgumentException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
		}
	}

	/** Run one post immediately (dry-run unless enabled + credentials are set). */
	@PostMapping("/api/admin/social/post-now")
	public SocialPost postNow(
			@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
			@RequestBody(required = false) PageChoice body) {
		requireAdmin(token);
		try {
			return social.runOnce("MANUAL", body == null ? null : blankToNull(body.pageKey()));
		} catch (IllegalArgumentException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
		}
	}

	public record PageChoice(String pageKey) {}

	/* ── auth ───────────────────────────────────────────────────────────── */

	private void requireAdmin(String token) {
		User u = sessionService.findUserByToken(token)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in required"));
		if (u.getRoles() != Role.ADMIN) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
		}
	}

	private static String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s.trim();
	}
}
