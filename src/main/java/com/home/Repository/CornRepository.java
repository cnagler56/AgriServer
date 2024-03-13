package com.home.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.home.Domain.CornYields;

@Repository
public interface CornRepository extends JpaRepository<CornYields, Long>{

}
