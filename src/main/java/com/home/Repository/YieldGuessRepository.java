package com.home.Repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.home.Domain.YieldGuess;

@Repository
public interface YieldGuessRepository extends JpaRepository<YieldGuess, Long> {
	List<YieldGuess> findByCommodityOrderByDateDesc(String commodity);
	List<YieldGuess> findByCommodityAndYear(String commodity, Integer year);

	/**
	 * Public Community Guesses list — only shared guesses. Legacy rows with a
	 * null `shared` (created before the column existed) are treated as shared.
	 */
	@Query("SELECT g FROM YieldGuess g WHERE g.commodity = :commodity "
		+ "AND (g.shared IS NULL OR g.shared = true) ORDER BY g.date DESC")
	List<YieldGuess> findSharedByCommodity(@Param("commodity") String commodity);
}
