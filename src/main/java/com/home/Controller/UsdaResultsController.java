package com.home.Controller;

import java.util.Map;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.home.Service.UsdaResultsService;

/**
 * /api/usda-results/{commodity} — community leaderboards scoring everyone's
 * yield guesses against USDA's actual national number.
 */
@RestController
@RequestMapping("/api/usda-results")
@CrossOrigin(origins = "http://localhost:3000", allowCredentials = "true")
public class UsdaResultsController {

	private final UsdaResultsService service;

	public UsdaResultsController(UsdaResultsService service) {
		this.service = service;
	}

	@GetMapping("/{commodity}")
	public Map<String, Object> results(
			@PathVariable String commodity,
			@RequestParam(required = false) String period) {
		return service.getResults(commodity, period);
	}
}
