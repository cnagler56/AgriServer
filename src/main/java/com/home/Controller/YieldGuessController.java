package com.home.Controller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.home.Domain.User;
import com.home.Domain.YieldGuess;
import com.home.Repository.YieldGuessRepository;
import com.home.Service.SessionService;

@RestController
@RequestMapping("/api/yield-guess")
@CrossOrigin(origins = "http://localhost:3000", allowCredentials = "true")
public class YieldGuessController {

	private static final int MAX_NOTE = 280;

	private final YieldGuessRepository repo;
	private final SessionService sessionService;

	public YieldGuessController(YieldGuessRepository repo, SessionService sessionService) {
		this.repo = repo;
		this.sessionService = sessionService;
	}

	/**
	 * Public roster: one current standing per user for the active crop year, each
	 * decorated with how it changed since that user's previous guess.
	 */
	@GetMapping("/{commodity}")
	public List<RosterEntry> roster(@PathVariable String commodity) {
		String c = commodity.toUpperCase();
		int currentYear = java.time.Year.now().getValue();
		Integer year = repo.findActiveYear(c, currentYear);
		if (year == null) return List.of();
		return foldRoster(repo.findRevisions(c, year, currentYear));
	}

	/** One user's change log for the active crop year (oldest → newest). */
	@GetMapping("/{commodity}/history/{userId}")
	public List<YieldGuess> history(@PathVariable String commodity, @PathVariable Long userId) {
		String c = commodity.toUpperCase();
		int currentYear = java.time.Year.now().getValue();
		Integer year = repo.findActiveYear(c, currentYear);
		if (year == null) return List.of();
		return repo.findUserRevisions(c, userId, year, currentYear);
	}

	/**
	 * Submit (or revise) a guess. Always an append — each call is a new immutable
	 * revision. Identity comes from the session, never the request body.
	 */
	@PostMapping
	public YieldGuess submit(
			@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
			@RequestBody YieldGuess guess) {

		User user = sessionService.findUserByToken(token)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in to submit a guess"));

		guess.setId(null);
		guess.setUserId(user.getUserId());                 // auth identity wins
		if (guess.getCommodity() != null) guess.setCommodity(guess.getCommodity().toUpperCase());

		// Fall back to the profile for any display field the form left blank.
		if (isBlank(guess.getName())     && user.getName()     != null) guess.setName(user.getName());
		if (isBlank(guess.getState())    && user.getState()    != null) guess.setState(user.getState());
		if (isBlank(guess.getInterest()) && user.getInterest() != null) guess.setInterest(user.getInterest());

		if (guess.getNote() != null) {
			String n = guess.getNote().trim();
			if (n.length() > MAX_NOTE) n = n.substring(0, MAX_NOTE);
			guess.setNote(n.isEmpty() ? null : n);
		}
		return repo.save(guess);
	}

	/* ── roster folding ─────────────────────────────────────────────────── */

	/** Fold oldest-first revisions into one current entry per user, with change info. */
	private List<RosterEntry> foldRoster(List<YieldGuess> revsAsc) {
		// Group by identity, preserving first-seen order. Rows without a userId
		// (legacy/anonymous) each stand alone — they have no comparable history.
		Map<String, List<YieldGuess>> byUser = new LinkedHashMap<>();
		for (YieldGuess g : revsAsc) {
			String key = g.getUserId() != null ? "u" + g.getUserId() : "row" + g.getId();
			byUser.computeIfAbsent(key, k -> new ArrayList<>()).add(g);
		}

		List<RosterEntry> out = new ArrayList<>();
		for (List<YieldGuess> revs : byUser.values()) {
			YieldGuess latest = revs.get(revs.size() - 1);
			YieldGuess prev   = revs.size() > 1 ? revs.get(revs.size() - 2) : null;

			Double delta = null;
			String direction = null;
			if (prev != null && latest.getEstimate() != null && prev.getEstimate() != null) {
				delta = round1(latest.getEstimate() - prev.getEstimate());
				direction = delta > 0 ? "up" : delta < 0 ? "down" : "same";
			}

			out.add(new RosterEntry(
				latest.getId(), latest.getUserId(), latest.getName(), latest.getState(), latest.getInterest(),
				latest.getEstimate(), latest.getDate() == null ? null : latest.getDate().toString(),
				revs.size(), revs.size() > 1, direction, delta,
				prev == null ? null : prev.getEstimate(), latest.getNote()));
		}
		// Newest activity first. LocalDateTime#toString is ISO-8601, so string order == time order.
		out.sort((a, b) -> {
			if (a.date() == null) return 1;
			if (b.date() == null) return -1;
			return b.date().compareTo(a.date());
		});
		return out;
	}

	private static boolean isBlank(String s) { return s == null || s.isBlank(); }
	private static double round1(double v) { return Math.round(v * 10.0) / 10.0; }

	/** One person's current standing in the challenge, plus how it moved. */
	public record RosterEntry(
		Long latestId,
		Long userId,
		String name,
		String state,
		String interest,
		Double estimate,          // latest estimate
		String date,              // latest revision time (ISO-8601)
		int revisions,            // total revisions this user made
		boolean updated,          // revisions > 1
		String direction,         // "up" | "down" | "same" | null (first guess)
		Double delta,             // latest − previous (null on first guess)
		Double previousEstimate,  // prior estimate (null on first guess)
		String note               // explanation attached to the latest revision
	) {}
}
