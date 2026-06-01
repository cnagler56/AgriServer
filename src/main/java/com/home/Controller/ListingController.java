package com.home.Controller;

import java.util.List;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.home.Domain.Listing;
import com.home.Service.ListingService;

@RestController
@CrossOrigin(origins = "http://localhost:3000")
public class ListingController {

	private final ListingService listingService;

	public ListingController(ListingService listingService) {
		this.listingService = listingService;
	}

	@GetMapping("/listings")
	public List<Listing> listings(
			@RequestParam(required = false) String type,
			@RequestParam(required = false) String category) {
		return this.listingService.getListings(type, category);
	}

	@PostMapping("/addlisting")
	public Listing addListing(@RequestBody Listing listing) {
		return this.listingService.addListing(listing);
	}
}
