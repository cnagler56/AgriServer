package com.home.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.home.Domain.OutlookSnapshot;

@Repository
public interface OutlookSnapshotRepository extends JpaRepository<OutlookSnapshot, Long> {

	Optional<OutlookSnapshot> findByIssuedDateAndRangeKeyAndElementAndLocation(
		LocalDate issuedDate, String rangeKey, String element, String location);

	/** Newest-first series for one range/element/location — take the top 3 for a trend. */
	List<OutlookSnapshot> findTop3ByRangeKeyAndElementAndLocationOrderByIssuedDateDesc(
		String rangeKey, String element, String location);

	boolean existsByIssuedDateAndRangeKeyAndElement(LocalDate issuedDate, String rangeKey, String element);
}
