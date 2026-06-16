package com.home.Controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.home.Service.PasswordResetService;

/**
 * Forgot-password endpoints.
 *
 *   POST /forgot-password { email }            → always 200 (no account enumeration)
 *   POST /reset-password  { token, password }  → 200 on success, 400 on bad/expired token
 */
@RestController
@CrossOrigin(origins = "http://localhost:3000", allowCredentials = "true")
public class PasswordResetController {

    private final PasswordResetService service;

    public PasswordResetController(PasswordResetService service) {
        this.service = service;
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgot(@RequestBody Map<String, String> body) {
        service.requestReset(body.get("email"));
        // Deliberately generic — never reveal whether the email is registered.
        return ResponseEntity.ok(Map.of(
            "message", "If an account exists for that email, a reset link is on its way."));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> reset(@RequestBody Map<String, String> body) {
        service.resetPassword(body.get("token"), body.get("password"));
        return ResponseEntity.ok(Map.of("message", "Your password has been reset. You can sign in now."));
    }
}
