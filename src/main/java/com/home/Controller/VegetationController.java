package com.home.Controller;

import java.util.Map;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.home.Service.VegetationService;

/**
 * County-level Vegetation Health Index for the Midwest — our own aggregation
 * of NOAA STAR's weekly 4km satellite composite. Public; backs the frontend's
 * Weather → Vegetation Health county map.
 */
@RestController
@CrossOrigin(origins = "http://localhost:3000", allowCredentials = "true")
public class VegetationController {

	private final VegetationService vegetation;

	public VegetationController(VegetationService vegetation) {
		this.vegetation = vegetation;
	}

	@GetMapping("/api/vegetation/counties")
	public Map<String, Object> counties() {
		return vegetation.getCounties();
	}
}
