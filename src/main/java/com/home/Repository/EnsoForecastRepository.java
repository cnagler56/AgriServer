package com.home.Repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.home.Domain.EnsoForecast;

@Repository
public interface EnsoForecastRepository extends JpaRepository<EnsoForecast, Long> {
	List<EnsoForecast> findAllByOrderBySeqAsc();
}
