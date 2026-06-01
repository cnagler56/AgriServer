package com.home.Repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.home.Domain.YieldGuess;

@Repository
public interface YieldGuessRepository extends JpaRepository<YieldGuess, Long> {
	List<YieldGuess> findByCommodityOrderByDateDesc(String commodity);
}
