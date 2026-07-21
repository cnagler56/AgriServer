package com.home.Controller;

import java.util.Map;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.home.Service.OutlookTrendService;

/**
 * Warmer/cooler, wetter/drier trend across the last few CPC extended outlooks,
 * sampled at Midwest state centroids. Public; backs the trend strip on the
 * frontend's Weather → Extended Outlook page.
 */
@RestController
@CrossOrigin(origins = "http://localhost:3000", allowCredentials = "true")
public class OutlookTrendController {

	private final OutlookTrendService outlook;

	public OutlookTrendController(OutlookTrendService outlook) {
		this.outlook = outlook;
	}

	@GetMapping("/api/outlook/trends")
	public Map<String, Object> trends() {
		return outlook.getTrends();
	}
}
