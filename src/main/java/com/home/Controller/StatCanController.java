package com.home.Controller;

import java.util.Map;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.home.Service.StatCanService;

/**
 * /api/statcan/{commodity} — Canada production (area, production, yield) from
 * Statistics Canada, national and by province. Read-only, open to all.
 */
@RestController
@RequestMapping("/api/statcan")
@CrossOrigin(origins = "http://localhost:3000", allowCredentials = "true")
public class StatCanController {

	private final StatCanService service;

	public StatCanController(StatCanService service) {
		this.service = service;
	}

	@GetMapping("/{commodity}")
	public Map<String, Object> production(@PathVariable String commodity) {
		return service.getProduction(commodity);
	}
}
