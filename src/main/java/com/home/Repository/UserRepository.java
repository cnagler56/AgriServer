package com.home.Repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.home.Domain.User;

@Repository
public interface UserRepository extends JpaRepository<User, Long>{
	 
	Optional<User> findByEmailAndPassword(String email, String password);
}
