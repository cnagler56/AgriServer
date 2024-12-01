package com.home.Repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.home.Domain.HogsData;

public interface HogRepository extends JpaRepository<HogsData, Long> {

}
