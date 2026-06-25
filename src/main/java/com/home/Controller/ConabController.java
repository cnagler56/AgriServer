package com.home.Controller;

import java.util.Map;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.home.Service.ConabService;

/**
 * /api/conab/{commodity} — Brazil production (area, production, yield) from CONAB,
 * with the crop-season split and top states. Read-only, open to all.
 */
@RestController
@RequestMapping("/api/conab")
@CrossOrigin(origins = "http://localhost:3000", allowCredentials = "true")
public class ConabController {

	private final ConabService service;

	public ConabController(ConabService service) {
		this.service = service;
	}

	@GetMapping("/{commodity}")
	public Map<String, Object> conab(@PathVariable String commodity) {
		return service.getProduction(commodity);
	}
}
