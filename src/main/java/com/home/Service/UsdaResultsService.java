package com.home.Service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.springframework.stereotype.Service;

import com.home.Domain.YieldGuess;
import com.home.Repository.YieldGuessRepository;
import com.home.Service.UsdaReportsService.PeriodYield;

/**
 * Scores the community's yield guesses against USDA's actual national number —
 * cheat-proof.
 *
 * Integrity rule: a guess only counts for a report period if it was submitted
 * STRICTLY BEFORE that report's publication time. So someone can't read USDA's
 * number and then post a "winning" guess — that guess lands after the cutoff and
 * is ineligible for the period (it can only count toward a future report).
 *
 * Each user is also deduped to their latest eligible guess so a serial guesser
 * can't stuff the boards.
 */
@Service
public class UsdaResultsService {

	private final UsdaReportsService reportsService;
	private final YieldGuessRepository guessRepo;

	public UsdaResultsService(UsdaReportsService reportsService, YieldGuessRepository guessRepo) {
		this.reportsService = reportsService;
		this.guessRepo = guessRepo;
	}

	/**
	 * @param requestedPeriod which report to score (e.g. "SEP", "YEAR"); null →
	 *                        the most recently published report.
	 */
	public Map<String, Object> getResults(String commodity, String requestedPeriod) {
		commodity = commodity.toUpperCase();
		List<PeriodYield> periods = reportsService.getNationalYieldByPeriod(commodity);

		Map<String, Object> out = new LinkedHashMap<>();
		out.put("commodity", commodity);

		if (periods.isEmpty()) {
			out.put("usdaYield", null);
			out.put("availablePeriods", List.of());
			out.put("byGroup", List.of());
			out.put("byState", List.of());
			out.put("topIndividuals", List.of());
			out.put("participants", 0);
			out.put("message", "USDA's national yield for " + commodity
				+ " isn't published yet — check back after the next Crop Production report.");
			return out;
		}

		// Choose the requested period, or default to the newest (periods is newest-first).
		PeriodYield chosen = periods.stream()
			.filter(p -> p.refPeriod().equalsIgnoreCase(requestedPeriod))
			.findFirst()
			.orElse(periods.get(0));

		final int year = chosen.year();
		final double usda = chosen.yield();
		final LocalDateTime cutoff = chosen.cutoff();

		out.put("year", year);
		out.put("period", chosen.refPeriod());
		out.put("usdaYield", usda);
		out.put("cutoff", cutoff == null ? null : cutoff.toString());
		out.put("availablePeriods", periods.stream()
			.map(p -> {
				Map<String, Object> m = new LinkedHashMap<>();
				m.put("period", p.refPeriod());
				m.put("usdaYield", p.yield());
				return m;
			})
			.toList());

		// Eligible guesses: this commodity + year, submitted STRICTLY before the
		// report's publication time. Then dedupe to each user's latest eligible guess.
		List<YieldGuess> raw = guessRepo.findByCommodityAndYear(commodity, year);
		Map<String, YieldGuess> latestByUser = new HashMap<>();
		for (YieldGuess g : raw) {
			if (g.getEstimate() == null || g.getDate() == null) continue;
			if (cutoff != null && !g.getDate().isBefore(cutoff)) continue;   // ← the cheat-proof gate

			String key = (g.getUserId() != null && g.getUserId() > 0)
				? "u:" + g.getUserId()
				: "n:" + (g.getName() == null ? "" : g.getName().trim().toLowerCase());
			YieldGuess existing = latestByUser.get(key);
			if (existing == null || g.getDate().isAfter(existing.getDate())) {
				latestByUser.put(key, g);
			}
		}
		Collection<YieldGuess> guesses = latestByUser.values();

		out.put("participants", guesses.size());
		out.put("byGroup", aggregate(guesses, usda, g -> normGroup(g.getInterest())));
		out.put("byState", aggregate(guesses, usda, g -> normState(g.getState())));
		out.put("topIndividuals", topIndividuals(guesses, usda));
		return out;
	}

	private List<Map<String, Object>> aggregate(Collection<YieldGuess> guesses, double usda,
			Function<YieldGuess, String> keyFn) {
		Map<String, double[]> acc = new HashMap<>();  // key → [sumEstimate, sumError, count]
		for (YieldGuess g : guesses) {
			double err = Math.abs(g.getEstimate() - usda);
			double[] a = acc.computeIfAbsent(keyFn.apply(g), k -> new double[3]);
			a[0] += g.getEstimate();
			a[1] += err;
			a[2] += 1;
		}
		return acc.entrySet().stream()
			.map(e -> {
				double[] a = e.getValue();
				int count = (int) a[2];
				Map<String, Object> m = new LinkedHashMap<>();
				m.put("label", e.getKey());
				m.put("count", count);
				m.put("avgEstimate", round1(a[0] / count));
				m.put("avgError", round1(a[1] / count));
				return m;
			})
			.sorted(Comparator.comparingDouble(m -> ((Number) m.get("avgError")).doubleValue()))
			.toList();
	}

	private List<Map<String, Object>> topIndividuals(Collection<YieldGuess> guesses, double usda) {
		return guesses.stream()
			.map(g -> {
				Map<String, Object> m = new LinkedHashMap<>();
				m.put("name", (g.getName() == null || g.getName().isBlank()) ? "Anonymous" : g.getName().trim());
				m.put("state", normState(g.getState()));
				m.put("group", normGroup(g.getInterest()));
				m.put("estimate", round1(g.getEstimate()));
				m.put("error", round1(Math.abs(g.getEstimate() - usda)));
				return m;
			})
			.sorted(Comparator.comparingDouble(m -> ((Number) m.get("error")).doubleValue()))
			.limit(10)
			.toList();
	}

	private static String normGroup(String s) { return (s == null || s.isBlank()) ? "Other" : s.trim(); }
	private static String normState(String s) { return (s == null || s.isBlank()) ? "Unknown" : s.trim().toUpperCase(); }
	private static double round1(double v) { return Math.round(v * 10) / 10.0; }
}
