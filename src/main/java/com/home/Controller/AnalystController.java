package com.home.Controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.home.Domain.AnalysisPost;
import com.home.Domain.AnalystSubscriber;
import com.home.Domain.Role;
import com.home.Domain.User;
import com.home.Repository.UserRepository;
import com.home.Service.AnalysisService;
import com.home.Service.SessionService;

/**
 * Provider console: an ANALYST authors/manages their posts and their own
 * subscriber list. Every endpoint requires publish permission.
 */
@RestController
@CrossOrigin(origins = "http://localhost:3000", allowCredentials = "true")
public class AnalystController {

	private final AnalysisService analysis;
	private final SessionService sessionService;
	private final UserRepository userRepo;

	public AnalystController(AnalysisService analysis, SessionService sessionService, UserRepository userRepo) {
		this.analysis = analysis;
		this.sessionService = sessionService;
		this.userRepo = userRepo;
	}

	/* ── admin: grant / revoke the analyst (publisher) role ─────────────── */

	/** ADMIN promotes a user to ANALYST (or back to USER) by email. */
	@PostMapping("/api/admin/analysts")
	public Map<String, Object> setAnalyst(
			@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
			@RequestBody AnalystGrant body) {
		User admin = sessionService.findUserByToken(token)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in required"));
		if (admin.getRoles() != Role.ADMIN) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
		}
		if (body == null || body.email() == null || body.email().isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email required");
		}
		User target = userRepo.findByEmail(body.email().trim())
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No account with that email"));
		if (target.getRoles() == Role.ADMIN) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That user is an admin");
		}
		target.setRoles(body.grant() ? Role.ANALYST : Role.USER);
		userRepo.save(target);
		return Map.of("email", target.getEmail(), "role", target.getRoles().name());
	}

	public record AnalystGrant(String email, boolean grant) {}

	/* ── posts ──────────────────────────────────────────────────────────── */

	@GetMapping("/api/analyst/posts")
	public List<AnalysisPost> myPosts(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token) {
		return analysis.myPosts(requirePublisher(token));
	}

	@PostMapping("/api/analyst/posts")
	public AnalysisPost savePost(
			@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
			@RequestBody PostInput body) {
		User author = requirePublisher(token);
		if (body == null || body.title() == null || body.title().isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A title is required.");
		}
		try {
			return analysis.savePost(author, body.id(), body.title(), body.body(), body.publish());
		} catch (SecurityException e) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
		} catch (IllegalArgumentException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
		}
	}

	@DeleteMapping("/api/analyst/posts/{id}")
	public Map<String, Object> deletePost(
			@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
			@PathVariable Long id) {
		try {
			analysis.deletePost(requirePublisher(token), id);
			return Map.of("ok", true);
		} catch (SecurityException e) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
		}
	}

	/* ── subscribers ────────────────────────────────────────────────────── */

	@GetMapping("/api/analyst/subscribers")
	public List<AnalystSubscriber> subscribers(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token) {
		return analysis.mySubscribers(requirePublisher(token));
	}

	@PostMapping("/api/analyst/subscribers")
	public AnalystSubscriber addSubscriber(
			@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
			@RequestBody SubscriberInput body) {
		try {
			return analysis.addSubscriber(requirePublisher(token),
				body == null ? null : body.email(), body == null ? null : body.note());
		} catch (IllegalArgumentException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
		}
	}

	@DeleteMapping("/api/analyst/subscribers/{id}")
	public Map<String, Object> removeSubscriber(
			@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
			@PathVariable Long id) {
		try {
			analysis.removeSubscriber(requirePublisher(token), id);
			return Map.of("ok", true);
		} catch (SecurityException e) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
		}
	}

	/* ── auth ───────────────────────────────────────────────────────────── */

	private User requirePublisher(String token) {
		User u = sessionService.findUserByToken(token)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in required"));
		if (!analysis.canPublish(u)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Analyst access required");
		}
		return u;
	}

	public record PostInput(Long id, String title, String body, boolean publish) {}
	public record SubscriberInput(String email, String note) {}
}
