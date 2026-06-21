package com.home.Controller;

import java.util.Map;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.home.Service.SupplyDemandService;

/**
 * /api/supply-demand/{commodity} — cached USDA supply/demand (WASDE) balance
 * sheet for one commodity, with US and World sections. Read-only, open to all.
 */
@RestController
@RequestMapping("/api/supply-demand")
@CrossOrigin(origins = "http://localhost:3000", allowCredentials = "true")
public class SupplyDemandController {

	private final SupplyDemandService service;

	public SupplyDemandController(SupplyDemandService service) {
		this.service = service;
	}

	@GetMapping("/{commodity}")
	public Map<String, Object> balanceSheet(@PathVariable String commodity) {
		return service.getBalanceSheet(commodity);
	}
}
