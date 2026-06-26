package com.home.Repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.home.Domain.SupplyDemand;

@Repository
public interface SupplyDemandRepository extends JpaRepository<SupplyDemand, Long> {
	List<SupplyDemand> findByCommodityOrderByRegionAscSeqAsc(String commodity);

	/**
	 * Delete a commodity's cached rows in its own transaction. Needed because ingestAll()
	 * runs on a background thread where the service-level @Transactional proxy is bypassed,
	 * leaving the derived delete with no EntityManager transaction for its remove calls.
	 */
	@Transactional
	long deleteByCommodity(String commodity);

	long countByCommodity(String commodity);

	/** True if any cached row predates monthly-snapshot tagging — triggers a re-ingest. */
	boolean existsByMonthIsNull();

	/**
	 * Canary for the South America backfill: soybeans is the last commodity to gain
	 * SA regions, so if SOYBEANS/BRAZIL is absent we know the SA ingest hasn't run.
	 * (Checking a bare region was insufficient — corn could satisfy it on its own.)
	 */
	boolean existsByCommodityAndRegion(String commodity, String region);

	/** Distinct monthly snapshots currently loaded (newest first) — for the admin status view. */
	@Query("SELECT DISTINCT s.month FROM SupplyDemand s WHERE s.month IS NOT NULL ORDER BY s.month DESC")
	List<Integer> findDistinctMonths();
}
