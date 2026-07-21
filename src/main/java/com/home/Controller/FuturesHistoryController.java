package com.home.Controller;

import java.util.Map;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.home.Service.FuturesHistoryService;

/**
 * Daily OHLC history for a commodity's front-month futures contract — backs the
 * site's own candlestick price chart. Public.
 */
@RestController
@CrossOrigin(origins = "http://localhost:3000", allowCredentials = "true")
public class FuturesHistoryController {

	private final FuturesHistoryService history;

	public FuturesHistoryController(FuturesHistoryService history) {
		this.history = history;
	}

	@GetMapping("/api/futures/history/{commodity}")
	public Map<String, Object> history(@PathVariable String commodity) {
		return history.getHistory(commodity);
	}
}
