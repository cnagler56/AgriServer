package com.home.Repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.home.Domain.ForecastLocation;

@Repository
public interface ForecastLocationRepository extends JpaRepository<ForecastLocation, Long> {
	List<ForecastLocation> findAllByOrderByNameAsc();
}
