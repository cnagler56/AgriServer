package com.home.Controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.home.Domain.Post;
import com.home.Domain.User;
import com.home.Service.PostService;
import com.home.Service.SessionService;
import com.home.Service.UserService;

/**
 * Top-level controller — users, posts, auth (sign-in / sign-up / sign-out / who-am-I).
 *
 * Auth flow:
 *   - /login + /register issue an HttpOnly session cookie via {@link SessionService}.
 *   - /me reads that cookie and returns the User (or 401 if there's no live session).
 *   - /logout clears the cookie and invalidates the row.
 * The cookie name is {@link SessionService#COOKIE_NAME}.
 */
@RestController
@CrossOrigin(origins = "http://localhost:3000", allowCredentials = "true")
public class IndexController {

    private final UserService userService;
    private final PostService postService;
    private final SessionService sessionService;

    /**
     * Cookie attributes. Local dev (same-site, http) uses the defaults below.
     * Cross-site prod (Vercel frontend ↔ Railway backend over HTTPS) needs
     * SameSite=None + Secure=true, set via env: APP_COOKIE_SAMESITE=None,
     * APP_COOKIE_SECURE=true.
     */
    @Value("${APP_COOKIE_SAMESITE:Lax}")
    private String cookieSameSite;
    @Value("${APP_COOKIE_SECURE:false}")
    private boolean cookieSecure;

    public IndexController(UserService userService, PostService postService, SessionService sessionService) {
        this.userService = userService;
        this.postService = postService;
        this.sessionService = sessionService;
    }

    @GetMapping("/user")
    public Iterable<User> users() {
        return this.userService.getList();
    }

    @GetMapping("/posts")
    public Iterable<Post> post() {
        return this.postService.getPosts();
    }

    @GetMapping("/post/{idposts}")
    public Post posta(@RequestParam Long idposts) {
        return this.postService.getfullPost(idposts);
    }

    // ── Auth ─────────────────────────────────────────────────────────────

    @GetMapping("/login")
    public ResponseEntity<User> login(@RequestParam String email, @RequestParam String password) {
        User user = userService.login(email, password);
        return withSessionCookie(user);
    }

    @PostMapping("/register")
    public ResponseEntity<User> register(@RequestBody User user) {
        User created = userService.register(user);
        return withSessionCookie(created);
    }

    /** Returns the User for the cookie's session, or 401 if there's no live session. */
    @GetMapping("/me")
    public ResponseEntity<User> me(
            @CookieValue(name = SessionService.COOKIE_NAME, required = false) String token) {
        return sessionService.findUserByToken(token)
            .map(ResponseEntity::ok)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not signed in"));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = SessionService.COOKIE_NAME, required = false) String token) {
        sessionService.invalidate(token);
        return ResponseEntity.noContent()
            .header("Set-Cookie", clearedCookie().toString())
            .build();
    }

    @PostMapping("/addpost")
    public void addPost(@RequestBody Post post) {
        this.postService.addPost(post);
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    /**
     * Wrap a sign-in / sign-up response with a fresh session cookie.
     *
     * SameSite=Lax + Secure=false keeps this working for local development
     * across :3000 ↔ :8081 (same site, different ports). In production swap
     * to SameSite=None + Secure=true behind HTTPS.
     */
    private ResponseEntity<User> withSessionCookie(User user) {
        String token = sessionService.createSession(user.getUserId());
        ResponseCookie cookie = ResponseCookie.from(SessionService.COOKIE_NAME, token)
            .httpOnly(true)
            .secure(cookieSecure)
            .sameSite(cookieSameSite)
            .path("/")
            .maxAge(java.time.Duration.ofDays(30))
            .build();
        return ResponseEntity.ok()
            .header("Set-Cookie", cookie.toString())
            .body(user);
    }

    /** A cookie that immediately expires — used by /logout to clear the browser's copy. */
    private ResponseCookie clearedCookie() {
        return ResponseCookie.from(SessionService.COOKIE_NAME, "")
            .httpOnly(true)
            .secure(cookieSecure)
            .sameSite(cookieSameSite)
            .path("/")
            .maxAge(0)
            .build();
    }
}
