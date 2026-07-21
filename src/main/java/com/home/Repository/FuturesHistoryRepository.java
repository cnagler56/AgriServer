package com.home.Repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.home.Domain.FuturesHistory;

@Repository
public interface FuturesHistoryRepository extends JpaRepository<FuturesHistory, Long> {
	Optional<FuturesHistory> findByCommodity(String commodity);
}
