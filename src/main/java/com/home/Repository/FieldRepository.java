package com.home.Repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.home.Domain.Field;

@Repository
public interface FieldRepository extends JpaRepository<Field, Long> {
	List<Field> findByUserIdOrderByCreatedAtDesc(Long userId);
}
