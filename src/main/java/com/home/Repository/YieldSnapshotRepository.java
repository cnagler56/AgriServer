package com.home.Repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.home.Domain.YieldSnapshot;

@Repository
public interface YieldSnapshotRepository extends JpaRepository<YieldSnapshot, Long> {
	List<YieldSnapshot> findByCommodityAndYear(String commodity, Integer year);

	Optional<YieldSnapshot> findByCommodityAndStateAndYearAndReferencePeriod(
		String commodity, String state, Integer year, String referencePeriod);

	Optional<YieldSnapshot> findFirstByCommodityOrderByFetchedAtDesc(String commodity);
}
