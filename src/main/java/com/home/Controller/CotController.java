package com.home.Controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.home.Service.CotService;

/**
 * /api/cot/{commodity} — latest CFTC managed-money positioning for one commodity
 * (CORN, SOYBEANS, WHEAT, SOYBEAN_MEAL, SOYBEAN_OIL, LIVE_CATTLE, LEAN_HOGS).
 * Returns an empty object if that commodity isn't tracked / not loaded yet.
 */
@RestController
@RequestMapping("/api/cot")
@CrossOrigin(origins = "http://localhost:3000", allowCredentials = "true")
public class CotController {

	private final CotService service;

	public CotController(CotService service) {
		this.service = service;
	}

	@GetMapping("/{commodity}")
	public Map<String, Object> cot(@PathVariable String commodity) {
		Map<String, Object> out = new LinkedHashMap<>();
		CotService.Pos pos = service.getPosition(commodity.toUpperCase());
		if (pos == null) return out;
		out.put("reportDate", service.getReportDate());
		out.put("longs", pos.longs());
		out.put("shorts", pos.shorts());
		out.put("net", pos.net());
		out.put("netChange", pos.netChange());
		return out;
	}
}
