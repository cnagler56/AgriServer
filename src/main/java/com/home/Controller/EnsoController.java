package com.home.Controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jakarta.transaction.Transactional;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.home.Domain.EnsoForecast;
import com.home.Domain.Role;
import com.home.Domain.User;
import com.home.Repository.EnsoForecastRepository;
import com.home.Service.EnsoService;
import com.home.Service.SessionService;

/**
 * /api/enso — observed ONI (NOAA, auto-fetched) plus the admin-entered
 * probabilistic outlook. GET is open to all; updating the outlook is admin-only.
 */
@RestController
@RequestMapping("/api/enso")
@CrossOrigin(origins = "http://localhost:3000", allowCredentials = "true")
public class EnsoController {

	private final EnsoService service;
	private final EnsoForecastRepository forecastRepo;
	private final SessionService sessionService;

	public EnsoController(EnsoService service, EnsoForecastRepository forecastRepo, SessionService sessionService) {
		this.service = service;
		this.forecastRepo = forecastRepo;
		this.sessionService = sessionService;
	}

	@GetMapping
	public Map<String, Object> enso() {
		Map<String, Object> out = service.getEnso();
		List<EnsoForecast> rows = forecastRepo.findAllByOrderBySeqAsc();
		out.put("forecast", rows);
		out.put("forecastIssued", rows.isEmpty() ? null : rows.get(0).getIssued());
		return out;
	}

	/** Replace the probabilistic outlook with a new set (admin only). */
	@PostMapping("/forecast")
	@Transactional
	public List<EnsoForecast> updateForecast(
			@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
			@RequestBody ForecastInput body) {

		User user = sessionService.findUserByToken(token)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in required"));
		if (user.getRoles() != Role.ADMIN) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
		}

		forecastRepo.deleteAll();
		if (body == null || body.rows() == null) return List.of();

		List<EnsoForecast> saved = new ArrayList<>();
		int seq = 0;
		for (Row r : body.rows()) {
			if (r == null || r.season() == null || r.season().isBlank()) continue;
			EnsoForecast f = new EnsoForecast();
			f.setSeq(seq++);
			f.setSeason(r.season().trim());
			f.setElNino(r.elNino());
			f.setNeutral(r.neutral());
			f.setLaNina(r.laNina());
			f.setIssued(body.issued() == null ? null : body.issued().trim());
			saved.add(f);
		}
		return forecastRepo.saveAll(saved);
	}

	public record ForecastInput(String issued, List<Row> rows) {}
	public record Row(String season, Double elNino, Double neutral, Double laNina) {}
}
