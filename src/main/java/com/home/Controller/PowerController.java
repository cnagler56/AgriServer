package com.home.Controller;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.home.Service.PowerService;

/**
 * Exposes NASA POWER agronomic data via simple JSON endpoints.
 *
 *  - /api/soil-moisture — last N days of root-zone / surface / profile soil wetness
 *  - /api/gdd           — accumulated growing-degree-days from a planting date
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:3000")
public class PowerController {

	private final PowerService powerService;

	public PowerController(PowerService powerService) {
		this.powerService = powerService;
	}

	/**
	 * Last N days of soil moisture for a point.
	 * GWETTOP — surface layer (0–10 cm), GWETROOT — root zone (0–100 cm), GWETPROF — full profile.
	 * Values are 0.0–1.0 fractional water content (1 = saturated).
	 */
	@GetMapping("/soil-moisture")
	public List<Map<String, Object>> soilMoisture(
			@RequestParam double lat,
			@RequestParam double lon,
			@RequestParam(required = false, defaultValue = "14") int days) {
		// POWER typically lags ~2 days behind real time
		LocalDate end = LocalDate.now().minusDays(2);
		LocalDate start = end.minusDays(Math.max(1, days) - 1);
		return powerService.fetchDaily(lat, lon, start, end, "GWETTOP,GWETROOT,GWETPROF");
	}

	/**
	 * Growing-degree-day accumulation from a planting date.
	 *
	 *   daily_gdd = clamp((Tmax + Tmin) / 2 − Tbase, 0, Tmax_cap − Tbase)
	 *
	 * Default Tbase=50°F, Tmax_cap=86°F (standard corn calculation).
	 * NASA POWER returns temperatures in °C, so we convert.
	 */
	@GetMapping("/gdd")
	public Map<String, Object> gdd(
			@RequestParam double lat,
			@RequestParam double lon,
			@RequestParam String plantedOn,         // YYYY-MM-DD
			@RequestParam(required = false, defaultValue = "50") double base,
			@RequestParam(required = false, defaultValue = "86") double cap) {
		LocalDate planted = LocalDate.parse(plantedOn);
		LocalDate end = LocalDate.now().minusDays(2);
		if (end.isBefore(planted)) {
			return Map.of("totalGdd", 0, "days", 0, "rows", List.of());
		}

		List<Map<String, Object>> raw = powerService.fetchDaily(
			lat, lon, planted, end, "T2M_MAX,T2M_MIN");

		double total = 0;
		int days = 0;
		for (Map<String, Object> row : raw) {
			Double tmaxC = numFromRow(row, "T2M_MAX");
			Double tminC = numFromRow(row, "T2M_MIN");
			if (tmaxC == null || tminC == null) {
				row.put("gdd", null);
				continue;
			}
			double tmaxF = tmaxC * 9.0 / 5.0 + 32.0;
			double tminF = tminC * 9.0 / 5.0 + 32.0;
			double hi = Math.min(tmaxF, cap);
			double lo = Math.max(tminF, base);   // corn convention: Tmin clamped to base
			double gdd = Math.max(0, (hi + lo) / 2.0 - base);
			row.put("gdd", Math.round(gdd * 10) / 10.0);
			total += gdd;
			days++;
		}

		Map<String, Object> out = new HashMap<>();
		out.put("totalGdd", Math.round(total * 10) / 10.0);
		out.put("days", days);
		out.put("plantedOn", plantedOn);
		out.put("base", base);
		out.put("cap", cap);
		out.put("rows", raw);
		return out;
	}

	private static Double numFromRow(Map<String, Object> row, String key) {
		Object o = row.get(key);
		if (o instanceof Number) return ((Number) o).doubleValue();
		return null;
	}
}
