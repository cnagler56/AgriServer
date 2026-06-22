package com.home.Controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.home.Domain.Feedback;
import com.home.Domain.Role;
import com.home.Domain.User;
import com.home.Repository.FeedbackRepository;
import com.home.Service.SessionService;

/**
 * Contact Us feedback. Anyone can POST a message; if they're signed in we stamp
 * their identity automatically. Reading the inbox (GET) is admin-only.
 */
@RestController
@RequestMapping("/api/feedback")
@CrossOrigin(origins = "http://localhost:3000", allowCredentials = "true")
public class FeedbackController {

	private static final int MAX_MESSAGE = 4000;

	private final FeedbackRepository repo;
	private final SessionService sessionService;

	public FeedbackController(FeedbackRepository repo, SessionService sessionService) {
		this.repo = repo;
		this.sessionService = sessionService;
	}

	@PostMapping
	public ResponseEntity<Feedback> submit(
			@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
			@RequestBody Feedback body) {

		if (body.getMessage() == null || body.getMessage().isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message is required");
		}
		if (body.getMessage().length() > MAX_MESSAGE) {
			body.setMessage(body.getMessage().substring(0, MAX_MESSAGE));
		}

		// If signed in, the authenticated identity wins over anything in the body.
		sessionService.findUserByToken(token).ifPresent(u -> {
			body.setUserId(u.getUserId());
			if (u.getName()  != null && !u.getName().isBlank())  body.setName(u.getName());
			if (u.getEmail() != null && !u.getEmail().isBlank()) body.setEmail(u.getEmail());
		});

		body.setId(null);
		Feedback saved = repo.save(body);
		return ResponseEntity.status(HttpStatus.CREATED).body(saved);
	}

	/** Admin-only inbox. */
	@GetMapping
	public List<Feedback> list(
			@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token) {
		User user = sessionService.findUserByToken(token)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in required"));
		if (user.getRoles() != Role.ADMIN) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
		}
		return repo.findAllByOrderByDateDesc();
	}
}
