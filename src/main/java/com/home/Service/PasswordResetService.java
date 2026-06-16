package com.home.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;

import jakarta.transaction.Transactional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.home.Domain.PasswordResetToken;
import com.home.Domain.User;
import com.home.Repository.PasswordResetTokenRepository;
import com.home.Repository.UserRepository;
import com.home.Repository.UserSessionRepository;

/**
 * Forgot-password flow.
 *
 *   requestReset(email)  → mints a token, emails a reset link. Returns quietly
 *                          whether or not the email is registered (no account
 *                          enumeration).
 *   resetPassword(token, newPassword)
 *                        → validates the token, sets the new (hashed) password,
 *                          burns the token, and signs the user out everywhere.
 */
@Service
public class PasswordResetService {

    private static final int TOKEN_BYTES = 32;            // 256-bit raw token
    private static final int TOKEN_TTL_MINUTES = 60;      // links expire after 1 hour

    private final UserRepository userRepo;
    private final PasswordResetTokenRepository tokenRepo;
    private final UserSessionRepository sessionRepo;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final SecureRandom random = new SecureRandom();

    @Value("${app.frontend.url:http://localhost:3000}")
    private String frontendUrl;

    public PasswordResetService(UserRepository userRepo,
            PasswordResetTokenRepository tokenRepo,
            UserSessionRepository sessionRepo,
            PasswordEncoder passwordEncoder,
            EmailService emailService) {
        this.userRepo = userRepo;
        this.tokenRepo = tokenRepo;
        this.sessionRepo = sessionRepo;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
    }

    /**
     * Begin a reset. Always returns without error so callers can't probe which
     * emails have accounts. Only sends mail if the email actually matches a user.
     */
    @Transactional
    public void requestReset(String email) {
        if (email == null || email.isBlank()) return;
        Optional<User> maybe = userRepo.findByEmail(email.trim());
        if (maybe.isEmpty()) {
            System.out.println("[RESET] requested for unknown email — no-op");
            return;
        }
        User user = maybe.get();

        // One live token per user — drop any previous ones
        tokenRepo.deleteByUserId(user.getUserId());

        String rawToken = randomToken();
        PasswordResetToken t = new PasswordResetToken();
        t.setTokenHash(sha256(rawToken));
        t.setUserId(user.getUserId());
        LocalDateTime now = LocalDateTime.now();
        t.setCreatedAt(now);
        t.setExpiresAt(now.plusMinutes(TOKEN_TTL_MINUTES));
        tokenRepo.save(t);

        String link = frontendUrl + "/reset-password?token=" + rawToken;
        emailService.send(
            user.getEmail(),
            "Reset your Just4Ag password",
            """
            We received a request to reset your Just4Ag password.

            Click the link below to choose a new one. It expires in %d minutes
            and can only be used once:

            %s

            If you didn't request this, you can safely ignore this email — your
            password won't change.
            """.formatted(TOKEN_TTL_MINUTES, link));
    }

    /**
     * Complete a reset. Throws 400 if the token is invalid/expired or the
     * password is too short.
     */
    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        if (rawToken == null || rawToken.isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing reset token");
        if (newPassword == null || newPassword.length() < 6)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be at least 6 characters");

        PasswordResetToken t = tokenRepo.findByTokenHash(sha256(rawToken))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "This reset link is invalid or has already been used."));

        if (t.getExpiresAt().isBefore(LocalDateTime.now())) {
            tokenRepo.delete(t);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "This reset link has expired. Please request a new one.");
        }

        User user = userRepo.findById(t.getUserId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Account no longer exists"));

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepo.save(user);

        // Burn the token and sign out every existing session for safety
        tokenRepo.delete(t);
        sessionRepo.deleteByUserId(user.getUserId());

        System.out.println("[RESET] password changed for userId " + user.getUserId());
    }

    /** Daily cleanup of expired reset tokens so the table stays small. */
    @Scheduled(cron = "0 30 3 * * *", zone = "America/Chicago")
    @Transactional
    public void cleanupExpired() {
        int n = tokenRepo.deleteExpiredBefore(LocalDateTime.now());
        if (n > 0) System.out.println("[RESET] cleaned up " + n + " expired tokens");
    }

    private String randomToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** SHA-256 of the raw token, base64url-encoded — what we store and look up by. */
    private static String sha256(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] h = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(h);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
