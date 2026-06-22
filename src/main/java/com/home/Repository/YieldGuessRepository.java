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
	 * Crop year of the active challenge — the most recent year anyone has guessed.
	 * Legacy rows with a null year are treated as the current year so they don't
	 * drop out of the roster.
	 */
	@Query("SELECT MAX(COALESCE(g.year, :currentYear)) FROM YieldGuess g WHERE g.commodity = :commodity")
	Integer findActiveYear(@Param("commodity") String commodity, @Param("currentYear") Integer currentYear);

	/**
	 * Every revision for one crop year, oldest first — the service folds these
	 * into one current standing per user plus their change history.
	 */
	@Query("SELECT g FROM YieldGuess g WHERE g.commodity = :commodity "
		+ "AND COALESCE(g.year, :currentYear) = :year ORDER BY g.date ASC")
	List<YieldGuess> findRevisions(@Param("commodity") String commodity,
		@Param("year") Integer year, @Param("currentYear") Integer currentYear);

	/** One user's revision log for a crop year, oldest first (the change log). */
	@Query("SELECT g FROM YieldGuess g WHERE g.commodity = :commodity AND g.userId = :userId "
		+ "AND COALESCE(g.year, :currentYear) = :year ORDER BY g.date ASC")
	List<YieldGuess> findUserRevisions(@Param("commodity") String commodity,
		@Param("userId") Long userId, @Param("year") Integer year, @Param("currentYear") Integer currentYear);
}
