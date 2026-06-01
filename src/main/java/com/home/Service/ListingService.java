package com.home.Service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.home.Domain.Listing;
import com.home.Repository.ListingRepository;

@Service
public class ListingService {

	private final ListingRepository listingRepository;

	public ListingService(ListingRepository listingRepository) {
		this.listingRepository = listingRepository;
	}

	public List<Listing> getListings(String type, String category) {
		boolean hasType = type != null && !type.isBlank() && !"ALL".equalsIgnoreCase(type);
		boolean hasCat = category != null && !category.isBlank() && !"ALL".equalsIgnoreCase(category);

		if (hasType && hasCat) {
			return this.listingRepository.findByTypeAndCategoryOrderByDateDesc(type.toUpperCase(), category.toUpperCase());
		}
		if (hasType) {
			return this.listingRepository.findByTypeOrderByDateDesc(type.toUpperCase());
		}
		if (hasCat) {
			return this.listingRepository.findByCategoryOrderByDateDesc(category.toUpperCase());
		}
		return this.listingRepository.findAllByOrderByDateDesc();
	}

	public Listing addListing(Listing listing) {
		if (listing.getType() != null) listing.setType(listing.getType().toUpperCase());
		if (listing.getCategory() != null) listing.setCategory(listing.getCategory().toUpperCase());
		return this.listingRepository.save(listing);
	}
}
