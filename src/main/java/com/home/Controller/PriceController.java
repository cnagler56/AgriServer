package com.home.Controller;

import java.util.List;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.home.Domain.CommodityGroup;
import com.home.Service.PriceService;

@RestController
@CrossOrigin(origins = "http://localhost:3000", allowCredentials = "true")
public class PriceController {

	private final PriceService priceService;

	public PriceController(PriceService priceService) {
		this.priceService = priceService;
	}

	/**
	 * Each commodity with its next 5 unexpired contracts. Front month is first.
	 */
	@GetMapping("/prices")
	public List<CommodityGroup> prices() {
		return priceService.getPricesGrouped();
	}
}
