package com.home.Controller;

import java.util.Map;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.home.Service.EthanolService;

/**
 * /api/ethanol — weekly EIA ethanol production & stocks, plus the implied corn
 * grind. Cached server-side and refreshed daily; read-only, open to all.
 */
@RestController
@RequestMapping("/api/ethanol")
@CrossOrigin(origins = "http://localhost:3000", allowCredentials = "true")
public class EthanolController {

	private final EthanolService service;

	public EthanolController(EthanolService service) {
		this.service = service;
	}

	@GetMapping
	public Map<String, Object> ethanol() {
		return service.getEthanol();
	}
}
