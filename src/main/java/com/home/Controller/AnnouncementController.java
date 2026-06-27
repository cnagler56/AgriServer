package com.home.Controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.transaction.Transactional;

import com.home.Domain.Announcement;
import com.home.Domain.Role;
import com.home.Domain.User;
import com.home.Repository.AnnouncementRepository;
import com.home.Service.SessionService;

/**
 * Home-page announcement. GET /api/announcement is open and returns the active
 * announcement (or an inactive placeholder when there's nothing to show). The
 * admin endpoints fetch the current row for editing and upsert it. One row is
 * maintained and reused, so editing is instant — no redeploy.
 */
@RestController
@CrossOrigin(origins = "http://localhost:3000", allowCredentials = "true")
public class AnnouncementController {

	private final AnnouncementRepository repo;
	private final SessionService sessionService;

	public AnnouncementController(AnnouncementRepository repo, SessionService sessionService) {
		this.repo = repo;
		this.sessionService = sessionService;
	}

	/** Public: the active announcement, or an inactive placeholder. */
	@GetMapping("/api/announcement")
	public Announcement get() {
		return repo.findTopByActiveTrueOrderByUpdatedAtDesc().orElseGet(this::inactivePlaceholder);
	}

	/** Admin: the current row (active or not) so the form can pre-fill. */
	@GetMapping("/api/admin/announcement")
	public Announcement adminGet(
			@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token) {
		requireAdmin(token);
		return repo.findTopByOrderByIdDesc().orElseGet(this::inactivePlaceholder);
	}

	/** Admin: create or update the single announcement row. */
	@PostMapping("/api/admin/announcement")
	@Transactional
	public Announcement save(
			@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
			@RequestBody AnnouncementInput body) {
		requireAdmin(token);

		Announcement a = repo.findTopByOrderByIdDesc().orElseGet(Announcement::new);
		a.setTitle(body == null || body.title() == null ? "" : body.title().trim());
		a.setBody(body == null || body.body() == null ? "" : body.body().trim());
		a.setActive(body != null && Boolean.TRUE.equals(body.active()));
		return repo.save(a);
	}

	private void requireAdmin(String token) {
		User user = sessionService.findUserByToken(token)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in required"));
		if (user.getRoles() != Role.ADMIN) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
		}
	}

	private Announcement inactivePlaceholder() {
		Announcement a = new Announcement();
		a.setActive(false);
		return a;
	}

	public record AnnouncementInput(String title, String body, Boolean active) {}
}
