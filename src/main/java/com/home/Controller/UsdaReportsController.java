package com.home.Controller;

import java.util.Map;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.home.Service.UsdaReportsService;

@RestController
@RequestMapping("/api/usda-reports")
@CrossOrigin(origins = "http://localhost:3000")
public class UsdaReportsController {

	private final UsdaReportsService service;

	public UsdaReportsController(UsdaReportsService service) {
		this.service = service;
	}

	/** /api/usda-reports/yield/CORN — current + prior-year-final state yields. */
	@GetMapping("/yield/{commodity}")
	public Map<String, Object> yield(@PathVariable String commodity) {
		return service.getYieldData(commodity);
	}

	/** /api/usda-reports/planting/CORN — current + prior-year planted acres by state. */
	@GetMapping("/planting/{commodity}")
	public Map<String, Object> planting(@PathVariable String commodity) {
		return service.getPlantingData(commodity);
	}
}
