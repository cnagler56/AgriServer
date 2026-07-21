package com.home.Repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.home.Domain.VegetationSnapshot;

@Repository
public interface VegetationSnapshotRepository extends JpaRepository<VegetationSnapshot, Long> {

	List<VegetationSnapshot> findByYearAndWeek(Integer year, Integer week);

	Optional<VegetationSnapshot> findByFipsAndYearAndWeek(String fips, Integer year, Integer week);

	/** Sortable key (year*100+week) of the newest stored week, or null when empty. */
	@Query("SELECT MAX(v.year * 100 + v.week) FROM VegetationSnapshot v")
	Integer latestKey();
}
