

package com.home.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.home.Domain.Beans;

@Repository
public interface GrainRepository extends JpaRepository <Beans, Long> {

}
