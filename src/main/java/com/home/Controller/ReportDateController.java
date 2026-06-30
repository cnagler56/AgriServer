package com.home.Controller;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.home.Domain.ReportReleaseDate;
import com.home.Domain.Role;
import com.home.Domain.User;
import com.home.Service.ReportScheduleService;
import com.home.Service.SessionService;

/**
 * Admin-managed USDA report release dates. The release-day refresh burst fires
 * only on the dates listed here, so report data lands within minutes of release
 * without hard-coding USDA's shifting calendar. Admin-only.
 */
@RestController
@CrossOrigin(origins = "http://localhost:3000", allowCredentials = "true")
public class ReportDateController {

	private final ReportScheduleService service;
	private final SessionService sessionService;

	public ReportDateController(ReportScheduleService service, SessionService sessionService) {
		this.service = service;
		this.sessionService = sessionService;
	}

	@GetMapping("/api/admin/report-dates")
	public List<ReportReleaseDate> get(
			@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token) {
		requireAdmin(token);
		return service.all();
	}

	/** Replace one report's dates with the supplied ISO-date list. */
	@PostMapping("/api/admin/report-dates")
	public List<ReportReleaseDate> set(
			@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
			@RequestBody ReportDatesInput body) {
		requireAdmin(token);
		List<LocalDate> dates = new ArrayList<>();
		if (body != null && body.dates() != null) {
			for (String s : body.dates()) {
				if (s == null || s.isBlank()) continue;
				try { dates.add(LocalDate.parse(s.trim())); }
				catch (Exception e) {
					throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid date: " + s);
				}
			}
		}
		try {
			return service.setDates(body == null ? null : body.reportKey(), dates);
		} catch (IllegalArgumentException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
		}
	}

	private void requireAdmin(String token) {
		User user = sessionService.findUserByToken(token)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in required"));
		if (user.getRoles() != Role.ADMIN) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
		}
	}

	public record ReportDatesInput(String reportKey, List<String> dates) {}
}
