package com.home.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.net.URI;

import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.home.Domain.CropProgressData;
import com.home.Domain.NASSYieldData;
import com.home.Repository.NASSYieldDataRepository;
import com.home.Service.ApiResponse.ApiItem;


@Service
public class NASSYieldService {

	@Value("${usda.api.key}")
	private String apiKey;

	private static final String NASS_URL = "https://quickstats.nass.usda.gov/api/api_GET/";

	private final RestTemplate restTemplate;
	private final NASSYieldDataRepository yieldDataRepository;

	public NASSYieldService(RestTemplate restTemplate, NASSYieldDataRepository yieldDataRepository) {
		this.restTemplate = restTemplate;
		this.yieldDataRepository = yieldDataRepository;
	}

	/**
	 * Fetch data from the NASS Quick Stats API.
	 *
	 * @param commodity   e.g. "CORN", "SOYBEANS", "WHEAT"
	 * @param year        e.g. "2024"
	 * @param statistic   e.g. "YIELD" or "AREA HARVESTED"
	 * @return ApiResponse from NASS (data list may be empty/null)
	 */
	public ApiResponse fetchDataWithParameters(String commodity, String year, String statistic) {
		// IMPORTANT: build to a URI so spaces in values like "AREA HARVESTED" get URL-encoded.
		// .toUriString() does NOT encode by default and RestTemplate then chokes on the raw space.
		URI url = UriComponentsBuilder.fromHttpUrl(NASS_URL)
				.queryParam("key", apiKey)
				.queryParam("commodity_desc", commodity)
				.queryParam("year", year)
				.queryParam("statisticcat_desc", statistic)
				.queryParam("agg_level_desc", "STATE")     // state-level only — no county/national noise
				.queryParam("source_desc", "SURVEY")       // annual SURVEY data (not CENSUS)
				.queryParam("reference_period_desc", "YEAR")
				.queryParam("format", "JSON")
				.build()
				.encode()
				.toUri();

		System.out.println("[NASS] " + url);
		try {
			ApiResponse resp = restTemplate.getForObject(url, ApiResponse.class);
			return resp != null ? resp : new ApiResponse();
		} catch (RestClientException e) {
			System.err.println("[NASS] call failed for " + commodity + "/" + statistic + "/" + year
					+ ": " + e.getMessage());
			return new ApiResponse();
		} catch (Exception e) {
			// Catch-all so a bad URL or JSON shape never returns 500 to the browser
			System.err.println("[NASS] unexpected error for " + commodity + "/" + statistic + "/" + year
					+ ": " + e.getClass().getSimpleName() + " - " + e.getMessage());
			return new ApiResponse();
		}
	}

	/**
	 * One-time cache warm: pull this year's YIELD for corn/beans/wheat and persist.
	 * Call manually via /api/fetch-recent — NOT from inside fetchDataWithParameters.
	 */
	public void fetchAndSaveMostRecentData() {
		String[] commodities = { "CORN", "SOYBEANS", "WHEAT" };
		String year = "2024";

		for (String commodity : commodities) {
			ApiResponse yieldResponse = fetchDataWithParameters(commodity, year, "YIELD");
			if (yieldResponse != null && yieldResponse.getData() != null) {
				for (ApiItem yieldItem : yieldResponse.getData()) {
					NASSYieldData entity = new NASSYieldData();
					entity.setCommodity(yieldItem.getCommodity());
					entity.setState(yieldItem.getState());
					entity.setYield(yieldItem.getYield());
					entity.setLoadTime(yieldItem.getLoad_time());
					yieldDataRepository.save(entity);
				}
			}
		}
	}

	/**
	 * Fetch state-level YIELD for the last `yearsBack` years (inclusive of current year),
	 * paired with AREA HARVESTED so the chart can compute a production-weighted national
	 * average (a plain mean of state yields overstates badly — small high-yield states
	 * count the same as Kansas).
	 */
	public List<NASSYieldData> fetchYieldHistory(String grain, int yearsBack) {
		int currentYear = java.time.Year.now().getValue();
		int yearGE = currentYear - Math.max(1, yearsBack) + 1;

		// Acres for the same range, keyed by state|class|year, so each yield row can be weighted.
		Map<String, String> acresMap = new HashMap<>();
		try {
			URI acresUrl = UriComponentsBuilder.fromHttpUrl(NASS_URL)
					.queryParam("key", apiKey)
					.queryParam("commodity_desc", grain)
					.queryParam("statisticcat_desc", "AREA HARVESTED")
					.queryParam("agg_level_desc", "STATE")
					.queryParam("source_desc", "SURVEY")
					.queryParam("reference_period_desc", "YEAR")
					.queryParam("year__GE", yearGE)
					.build().encode().toUri();
			ApiResponse acresResp = restTemplate.getForObject(acresUrl, ApiResponse.class);
			if (acresResp != null && acresResp.getData() != null) {
				for (ApiItem a : acresResp.getData()) {
					acresMap.put(a.getState() + "|" + classPrefix(a.getShortDesc()) + "|" + a.getYear(), a.getYield());
				}
			}
		} catch (Exception e) {
			System.err.println("[NASS HISTORY] acres fetch failed (chart will fall back to simple avg): " + e.getMessage());
		}

		URI url = UriComponentsBuilder.fromHttpUrl(NASS_URL)
				.queryParam("key", apiKey)
				.queryParam("commodity_desc", grain)
				.queryParam("statisticcat_desc", "YIELD")
				.queryParam("agg_level_desc", "STATE")
				.queryParam("source_desc", "SURVEY")
				.queryParam("reference_period_desc", "YEAR")
				.queryParam("year__GE", yearGE)   // NASS supports double-underscore comparison ops
				.build()
				.encode()
				.toUri();

		System.out.println("[NASS HISTORY] " + url);

		try {
			ApiResponse resp = restTemplate.getForObject(url, ApiResponse.class);
			if (resp == null || resp.getData() == null) {
				return new ArrayList<>();
			}
			List<NASSYieldData> out = new ArrayList<>();
			for (ApiItem item : resp.getData()) {
				NASSYieldData entity = new NASSYieldData();
				entity.setCommodity(item.getCommodity());
				entity.setShortDesc(item.getShortDesc());
				entity.setState(item.getState());
				entity.setYear(item.getYear());
				entity.setYield(item.getYield());
				entity.setAcresValue(acresMap.getOrDefault(
					item.getState() + "|" + classPrefix(item.getShortDesc()) + "|" + item.getYear(), "N/A"));
				entity.setLoadTime(item.getLoad_time());
				out.add(entity);
			}
			System.out.println("[NASS HISTORY] " + out.size() + " rows for " + grain + " back to " + yearGE);
			return out;
		} catch (Exception e) {
			System.err.println("[NASS HISTORY] error: " + e.getClass().getSimpleName() + " - " + e.getMessage());
			return new ArrayList<>();
		}
	}

	/**
	 * Live fetch (not persisted) — returns YIELD merged with AREA HARVESTED for the given grain/year.
	 * The `month` parameter is accepted for backwards-compat but unused: yield/acres are annual.
	 */
	public List<NASSYieldData> fetchNASSYieldData(String grain, String month, String year) {
		ApiResponse yieldResponse = fetchDataWithParameters(grain, year, "YIELD");
		ApiResponse acresResponse = fetchDataWithParameters(grain, year, "AREA HARVESTED");
		return consolidate(yieldResponse, acresResponse);
	}

	/** Build a row per (state, class) that combines yield + acres. */
	private List<NASSYieldData> consolidate(ApiResponse yieldResponse, ApiResponse acresResponse) {
		// Pair by (state, class prefix) so e.g. "CORN, GRAIN" yields don't get matched to
		// "CORN, SILAGE" acres. The class prefix is the bit before " - " (the stat dash).
		Map<String, String> acresMap = new HashMap<>();
		if (acresResponse != null && acresResponse.getData() != null) {
			for (ApiItem a : acresResponse.getData()) {
				acresMap.put(a.getState() + "|" + classPrefix(a.getShortDesc()), a.getYield());
			}
		}

		List<NASSYieldData> out = new ArrayList<>();
		if (yieldResponse != null && yieldResponse.getData() != null) {
			for (ApiItem y : yieldResponse.getData()) {
				NASSYieldData entity = new NASSYieldData();
				entity.setCommodity(y.getCommodity());
				entity.setShortDesc(y.getShortDesc());
				entity.setState(y.getState());
				entity.setYield(y.getYield());
				entity.setAcresValue(acresMap.getOrDefault(y.getState() + "|" + classPrefix(y.getShortDesc()), "N/A"));
				entity.setLoadTime(y.getLoad_time());
				out.add(entity);
			}
		}
		return out;
	}

	/**
	 * Extracts the commodity+class part of a NASS short_desc.
	 * "CORN, GRAIN - YIELD, MEASURED IN BU / ACRE" → "CORN, GRAIN"
	 * "CORN - YIELD, MEASURED IN BU / ACRE"        → "CORN"
	 */
	private static String classPrefix(String shortDesc) {
		if (shortDesc == null) return "";
		int idx = shortDesc.indexOf(" - ");
		return (idx == -1 ? shortDesc : shortDesc.substring(0, idx)).trim();
	}

	/**
	 * Fetch weekly NASS Crop Progress for a single commodity + year.
	 * Returns one row per (state, stage, week). The frontend pivots this for display.
	 */
	public List<CropProgressData> fetchCropProgress(String grain, int year) {
		URI url = UriComponentsBuilder.fromHttpUrl(NASS_URL)
				.queryParam("key", apiKey)
				.queryParam("commodity_desc", grain)
				.queryParam("statisticcat_desc", "PROGRESS")
				.queryParam("agg_level_desc", "STATE")
				.queryParam("year", year)
				.build()
				.encode()
				.toUri();

		System.out.println("[NASS PROGRESS] " + url);

		try {
			ApiResponse resp = restTemplate.getForObject(url, ApiResponse.class);
			if (resp == null || resp.getData() == null) return new ArrayList<>();

			List<CropProgressData> out = new ArrayList<>();
			for (ApiItem item : resp.getData()) {
				CropProgressData row = new CropProgressData();
				row.setCommodity(item.getCommodity());
				row.setShortDesc(item.getShortDesc());
				row.setState(item.getState());
				row.setYear(item.getYear());
				row.setUnit(item.getUnitDesc());                 // e.g. "PCT PLANTED"
				row.setWeekEnding(item.getWeekEnding());         // e.g. "2024-05-12"
				row.setValue(item.getYield());                   // ApiItem.yield maps to NASS "Value"
				out.add(row);
			}
			System.out.println("[NASS PROGRESS] " + out.size() + " rows for " + grain + " " + year);
			return out;
		} catch (Exception e) {
			System.err.println("[NASS PROGRESS] error: " + e.getClass().getSimpleName() + " - " + e.getMessage());
			return new ArrayList<>();
		}
	}
}
