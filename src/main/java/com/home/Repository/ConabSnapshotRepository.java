package com.home.Repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.home.Domain.ConabSnapshot;

public interface ConabSnapshotRepository extends JpaRepository<ConabSnapshot, Long> {

	/** The snapshot we already wrote this month for a crop (for upsert). */
	Optional<ConabSnapshot> findFirstByCommodityAndCropYearAndMonthKey(
		String commodity, String cropYear, Integer monthKey);

	/** Most recent earlier-month snapshot for the same crop year (for the MoM delta). */
	ConabSnapshot findFirstByCommodityAndCropYearAndMonthKeyLessThanOrderByMonthKeyDesc(
		String commodity, String cropYear, Integer monthKey);
}
