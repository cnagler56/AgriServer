package com.home.Domain;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * Server-side session row. One per active sign-in.
 *
 * The opaque token is what travels in the HttpOnly cookie. We look up the
 * row by token to resolve a request's user identity.
 *
 * Replaces the old client-side `localStorage.token = 'logged_in'` flag,
 * which was a value, not proof of authentication.
 */
@Entity
@Table(
    name = "user_sessions",
    indexes = { @Index(name = "idx_user_sessions_token", columnList = "token", unique = true) }
)
public class UserSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 128-bit URL-safe random string. Stored as the cookie value and looked
     * up to resolve the user. Never logged — treat it like a password.
     *
     * The unique constraint comes from the @Table index above so we don't
     * declare it twice (some Hibernate versions emit conflicting DDL when
     * both @Column(unique=true) and @Index(unique=true) target the same
     * column).
     */
    @JsonIgnore
    @Column(nullable = false, length = 64)
    private String token;

    @JsonProperty
    private Long userId;

    @JsonProperty
    private LocalDateTime createdAt;

    @JsonProperty
    private LocalDateTime expiresAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
}
