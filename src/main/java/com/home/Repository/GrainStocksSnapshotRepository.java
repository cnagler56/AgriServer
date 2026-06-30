package com.home.Repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.home.Domain.GrainStocksSnapshot;

@Repository
public interface GrainStocksSnapshotRepository extends JpaRepository<GrainStocksSnapshot, Long> {
	Optional<GrainStocksSnapshot> findByCommodity(String commodity);
}
