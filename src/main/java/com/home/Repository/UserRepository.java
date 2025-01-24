package com.home.Repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.home.Domain.MyUsers;

@Repository
public interface UserRepository extends JpaRepository<MyUsers, Long>{
	 
	Optional<MyUsers> findByUsername(String username);
	Optional<MyUsers> findByUsernameAndPassword(String username, String password);
	Optional<MyUsers> findByEmailAndPassword(String email, String password);
	Optional<MyUsers> findByEmail(String email);
 
}
