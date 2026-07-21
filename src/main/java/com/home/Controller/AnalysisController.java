package com.home.Controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.home.Domain.AnalysisPost;
import com.home.Domain.User;
import com.home.Service.AnalysisService;
import com.home.Service.SessionService;

/**
 * Reader side of the gated Analysis tab. The feed endpoint enforces the read
 * entitlement server-side (403 if not entitled) — hiding the tab in the UI is
 * only cosmetic.
 */
@RestController
@CrossOrigin(origins = "http://localhost:3000", allowCredentials = "true")
public class AnalysisController {

	private final AnalysisService analysis;
	private final SessionService sessionService;

	public AnalysisController(AnalysisService analysis, SessionService sessionService) {
		this.analysis = analysis;
		this.sessionService = sessionService;
	}

	/** Whether the current session can read and/or publish — drives nav/tab visibility. */
	@GetMapping("/api/analysis/access")
	public AnalysisService.Access access(
			@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token) {
		User u = sessionService.findUserByToken(token).orElse(null);
		return analysis.access(u);
	}

	/** The published analysis feed — entitled readers only. */
	@GetMapping("/api/analysis")
	public List<AnalysisPost> feed(
			@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token) {
		User u = sessionService.findUserByToken(token).orElse(null);
		if (!analysis.canRead(u)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This content is for subscribers.");
		}
		return analysis.feed();
	}

	/** Small metadata endpoint so the reader page can show a friendly gate. */
	@GetMapping("/api/analysis/meta")
	public Map<String, Object> meta(
			@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token) {
		User u = sessionService.findUserByToken(token).orElse(null);
		AnalysisService.Access a = analysis.access(u);
		return Map.of("signedIn", u != null, "canRead", a.canRead(), "canPublish", a.canPublish());
	}
}
