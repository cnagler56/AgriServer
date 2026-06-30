package com.home.Repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.home.Domain.ExportSalesSnapshot;

@Repository
public interface ExportSalesSnapshotRepository extends JpaRepository<ExportSalesSnapshot, Long> {
	Optional<ExportSalesSnapshot> findByCommodity(String commodity);
}
