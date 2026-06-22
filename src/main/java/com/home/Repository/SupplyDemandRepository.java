package com.home.Repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
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
}
