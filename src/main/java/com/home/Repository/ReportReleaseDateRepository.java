package com.home.Repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.home.Domain.ReportReleaseDate;

@Repository
public interface ReportReleaseDateRepository extends JpaRepository<ReportReleaseDate, Long> {

	boolean existsByReportKeyAndReleaseDate(String reportKey, LocalDate releaseDate);

	List<ReportReleaseDate> findAllByOrderByReportKeyAscReleaseDateAsc();

	/**
	 * Bulk delete that executes IMMEDIATELY, not at flush time. The derived
	 * form (plain {@code deleteByReportKey}) queues entity removals, and
	 * Hibernate flushes inserts before deletes — so replacing a report's
	 * dates with a list overlapping the old one hit the
	 * (report_key, release_date) unique constraint and 500'd the save.
	 */
	@Modifying
	@Query("DELETE FROM ReportReleaseDate r WHERE r.reportKey = :reportKey")
	void deleteByReportKey(@Param("reportKey") String reportKey);
}
