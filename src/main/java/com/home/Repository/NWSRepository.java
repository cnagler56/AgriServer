package com.home.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.home.Domain.Weather;

@Repository
public interface NWSRepository extends JpaRepository<Weather, Long> {

}
