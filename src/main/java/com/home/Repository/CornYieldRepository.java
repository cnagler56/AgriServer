package com.home.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.home.Domain.CornGuess;

@Repository
public interface CornYieldRepository extends JpaRepository<CornGuess,Long> {

}
