package com.home.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.home.Domain.UserSession;

@Repository
public interface UserSessionRepository extends JpaRepository<UserSession, Long> {

    Optional<UserSession> findByToken(String token);

    void deleteByToken(String token);

    /** Bulk delete of expired sessions. Called by the scheduled cleanup. */
    @Modifying
    @Query("DELETE FROM UserSession s WHERE s.expiresAt < :now")
    int deleteExpiredBefore(@Param("now") LocalDateTime now);
}
