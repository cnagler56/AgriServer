package com.home.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.home.Domain.BeanGuess;

@Repository
public interface BeanGuessRepository extends JpaRepository <BeanGuess, Long> {

}
