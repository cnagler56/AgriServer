package com.home.Controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.home.Domain.Role;
import com.home.Domain.User;
import com.home.Service.CropCommentaryService;
import com.home.Service.SessionService;
import com.home.Service.UsdaReportsService;

/**
 * Crop Production report summary — national yield / production / harvested
 * acres for the latest report, with month-over-month and year-over-year change
 * plus the biggest state yield movers. Backs the /report-summary tab.
 */
@RestController
@CrossOrigin(origins = "http://localhost:3000", allowCredentials = "true")
public class CropSummaryController {

	private final UsdaReportsService reports;
	private final CropCommentaryService commentary;
	private final SessionService sessionService;

	public CropSummaryController(UsdaReportsService reports, CropCommentaryService commentary,
			SessionService sessionService) {
		this.reports = reports;
		this.commentary = commentary;
		this.sessionService = sessionService;
	}

	/**
	 * Public summary for a commodity. Optional {@code year} reviews a past report
	 * (defaults to the current season, falling back to last year if none is out).
	 */
	@GetMapping("/api/crop-summary/{commodity}")
	public Map<String, Object> summary(
			@PathVariable String commodity,
			@RequestParam(required = false) Integer year) {
		return reports.getReportSummary(commodity, year);
	}

	/**
	 * Public: an AI-generated plain-English recap of the report, grounded in the
	 * same figures the summary endpoint returns. Cached per report period.
	 */
	@GetMapping("/api/crop-summary/{commodity}/commentary")
	public CropCommentaryService.Commentary commentary(
			@PathVariable String commodity,
			@RequestParam(required = false) Integer year) {
		return commentary.getCommentary(commodity, year);
	}

	/**
	 * Public: one AI recap covering all crops (corn, soybeans, wheat) in a single
	 * write-up, grounded in each crop's figures. The literal /all/ path takes
	 * precedence over the {commodity} route above, so it isn't a commodity lookup.
	 */
	@GetMapping("/api/crop-summary/all/commentary")
	public CropCommentaryService.Commentary combinedCommentary(
			@RequestParam(required = false) Integer year) {
		return commentary.getCombinedCommentary(year);
	}

	/**
	 * Admin: pull a past year's real yields + harvested acres from NASS so the
	 * summary can be tested against last year's published numbers.
	 */
	@PostMapping("/api/admin/crop-summary/backfill")
	public Map<String, Object> backfill(
			@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
			@RequestParam String commodity,
			@RequestParam int year) {
		requireAdmin(token);
		reports.backfillYear(commodity, year);
		return reports.getReportSummary(commodity, year);
	}

	private void requireAdmin(String token) {
		User user = sessionService.findUserByToken(token)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in required"));
		if (user.getRoles() != Role.ADMIN) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
		}
	}
}
