package com.home.Repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.home.Domain.PlantingSnapshot;

@Repository
public interface PlantingSnapshotRepository extends JpaRepository<PlantingSnapshot, Long> {
	List<PlantingSnapshot> findByCommodityAndYear(String commodity, Integer year);

	Optional<PlantingSnapshot> findByCommodityAndStateAndYearAndReferencePeriod(
		String commodity, String state, Integer year, String referencePeriod);

	Optional<PlantingSnapshot> findFirstByCommodityOrderByFetchedAtDesc(String commodity);
}
