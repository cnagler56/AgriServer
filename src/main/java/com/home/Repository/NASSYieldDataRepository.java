package com.home.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.home.Domain.NASSYieldData;

@Repository
public interface NASSYieldDataRepository extends JpaRepository<NASSYieldData, Long>  {

}
