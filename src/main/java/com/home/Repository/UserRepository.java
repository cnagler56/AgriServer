package com.home.Repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.home.Domain.User;

/**
 * Note on what's NOT here: the old `findByEmailAndPassword` and
 * `findByUsernameAndPassword` derived queries were removed when passwords
 * moved to BCrypt. Plaintext SQL comparison can't work against a hashed
 * column — always fetch by identifier, then verify the password with
 * {@code PasswordEncoder.matches(raw, stored)}.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

	Optional<User> findByUsername(String username);

	Optional<User> findByEmail(String email);
}
