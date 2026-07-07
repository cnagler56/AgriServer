package com.home.Controller;

import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.home.Domain.Role;
import com.home.Domain.User;
import com.home.Service.SessionService;
import com.home.Service.SiteSettingService;

/**
 * Which animated banner the home page shows. The banner components live in the
 * frontend; this just stores the admin's choice so it survives deploys and can
 * be changed without shipping code.
 */
@RestController
@CrossOrigin(origins = "http://localhost:3000", allowCredentials = "true")
public class HomeBannerController {

	private static final String KEY = "home_banner";
	private static final String DEFAULT_BANNER = "corn";
	/** Must match the registry in the frontend's HomeBanner component. */
	private static final Set<String> ALLOWED = Set.of("corn", "wheat", "rain", "crash", "none");

	private final SiteSettingService settings;
	private final SessionService sessionService;

	public HomeBannerController(SiteSettingService settings, SessionService sessionService) {
		this.settings = settings;
		this.sessionService = sessionService;
	}

	/** Public: the active banner key for the home page. */
	@GetMapping("/api/site/banner")
	public Map<String, String> get() {
		return Map.of("banner", settings.get(KEY, DEFAULT_BANNER));
	}

	/** Admin: switch the active banner. */
	@PostMapping("/api/admin/site/banner")
	public Map<String, String> set(
			@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
			@RequestBody Map<String, String> body) {
		requireAdmin(token);
		String banner = body == null ? null : body.get("banner");
		if (banner == null || !ALLOWED.contains(banner.trim().toLowerCase())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
				"banner must be one of " + ALLOWED);
		}
		return Map.of("banner", settings.set(KEY, banner.trim().toLowerCase()));
	}

	private void requireAdmin(String token) {
		User user = sessionService.findUserByToken(token)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in required"));
		if (user.getRoles() != Role.ADMIN) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
		}
	}
}
