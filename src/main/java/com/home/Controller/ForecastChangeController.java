package com.home.Controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.home.Domain.ForecastLocation;
import com.home.Domain.Role;
import com.home.Domain.User;
import com.home.Service.ForecastChangeService;
import com.home.Service.SessionService;

@RestController
@RequestMapping("/api/forecast-locations")
@CrossOrigin(origins = "http://localhost:3000", allowCredentials = "true")
public class ForecastChangeController {

	private final ForecastChangeService service;
	private final SessionService sessionService;

	public ForecastChangeController(ForecastChangeService service, SessionService sessionService) {
		this.service = service;
		this.sessionService = sessionService;
	}

	/**
	 * The tracked-location set is admin-curated. Reads are open to everyone;
	 * every write requires an ADMIN session. 401 if not signed in, 403 if signed
	 * in but not an admin.
	 */
	private void requireAdmin(String token) {
		User user = sessionService.findUserByToken(token)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in required"));
		if (user.getRoles() != Role.ADMIN) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
		}
	}

	@GetMapping
	public List<ForecastLocation> list() {
		return service.list();
	}

	@PostMapping
	public ForecastLocation create(
			@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
			@RequestBody ForecastLocation body) {
		requireAdmin(token);
		return service.create(body);
	}

	@PutMapping("/{id}")
	public ForecastLocation update(
			@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
			@PathVariable Long id, @RequestBody ForecastLocation body) {
		requireAdmin(token);
		return service.update(id, body);
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(
			@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
			@PathVariable Long id) {
		requireAdmin(token);
		service.delete(id);
		return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
	}

	@PostMapping("/{id}/refresh")
	public ForecastLocation refresh(
			@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
			@PathVariable Long id) {
		requireAdmin(token);
		return service.refresh(id);
	}

	/** Force-refresh every tracked location; returns the updated list. */
	@PostMapping("/refresh-all")
	public List<ForecastLocation> refreshAll(
			@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token) {
		requireAdmin(token);
		return service.refreshAll();
	}
}
