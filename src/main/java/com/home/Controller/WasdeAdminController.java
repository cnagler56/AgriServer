package com.home.Controller;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.home.Domain.Role;
import com.home.Domain.User;
import com.home.Service.SessionService;
import com.home.Service.SupplyDemandService;

/**
 * Admin-only WASDE CSV upload. Lets an admin drop in each month's
 * machine-readable WASDE file; it's stored in the DB and ingested immediately,
 * so Supply & Demand updates without a redeploy. Gated by the session cookie +
 * ADMIN role.
 */
@RestController
@RequestMapping("/api/admin/wasde")
@CrossOrigin(origins = "http://localhost:3000", allowCredentials = "true")
public class WasdeAdminController {

    private final SupplyDemandService supplyDemandService;
    private final SessionService sessionService;

    public WasdeAdminController(SupplyDemandService supplyDemandService, SessionService sessionService) {
        this.supplyDemandService = supplyDemandService;
        this.sessionService = sessionService;
    }

    /** Current loaded months + prior uploads (admin status view). */
    @GetMapping
    public Map<String, Object> status(
            @CookieValue(name = SessionService.COOKIE_NAME, required = false) String token) {
        requireAdmin(token);
        return supplyDemandService.uploadStatus();
    }

    /** Upload a WASDE CSV (multipart field "file"); stores + re-ingests it. */
    @PostMapping
    public Map<String, Object> upload(
            @CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
            @RequestParam("file") MultipartFile file) {
        requireAdmin(token);
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No file uploaded.");
        }
        String content;
        try {
            content = new String(file.getBytes(), StandardCharsets.ISO_8859_1);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not read the uploaded file.");
        }
        Map<String, Object> result = supplyDemandService.uploadAndIngest(file.getOriginalFilename(), content);
        if (Boolean.FALSE.equals(result.get("ok"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.valueOf(result.get("message")));
        }
        return result;
    }

    private void requireAdmin(String token) {
        User user = (token == null) ? null : sessionService.findUserByToken(token).orElse(null);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in required.");
        }
        if (user.getRoles() != Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admins only.");
        }
    }
}
