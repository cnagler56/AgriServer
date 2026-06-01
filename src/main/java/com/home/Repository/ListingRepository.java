package com.home.Repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.home.Domain.Listing;

@Repository
public interface ListingRepository extends JpaRepository<Listing, Long> {

	List<Listing> findAllByOrderByDateDesc();

	List<Listing> findByTypeOrderByDateDesc(String type);

	List<Listing> findByCategoryOrderByDateDesc(String category);

	List<Listing> findByTypeAndCategoryOrderByDateDesc(String type, String category);
}
