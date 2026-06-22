package com.home.Repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import com.home.Domain.NewsItem;

public interface NewsItemRepository extends JpaRepository<NewsItem, Long> {

	/** The live feed — items first seen within the roll-off window, newest first. */
	List<NewsItem> findByCreatedAtAfterOrderByCreatedAtDesc(LocalDateTime cutoff);

	boolean existsByDedupeKey(String dedupeKey);

	/** Housekeeping — drop long-expired items. Transactional (derived delete). */
	@Transactional
	long deleteByCreatedAtBefore(LocalDateTime cutoff);
}
