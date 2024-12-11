package com.home.Repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.home.Domain.CattleData;

 

public interface CattleRepository extends JpaRepository<CattleData, Long> {

}
