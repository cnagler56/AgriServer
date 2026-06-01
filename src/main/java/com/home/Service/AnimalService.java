package com.home.Service;

import java.net.URI;
import java.util.Collections;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.home.Domain.CattleData;
import com.home.Domain.CattleResponse;
import com.home.Domain.HogResponse;
import com.home.Domain.HogsData;
import com.home.Domain.NASSYieldData;
import com.home.Repository.HogRepository;
import com.home.Repository.NASSYieldDataRepository;

@Service
public class AnimalService {

    @Value("${usda.api.key}")
    private String apiKey;
    
    private final RestTemplate restTemplate;
    private final HogRepository hogRepository;
    
    public AnimalService(RestTemplate restTemplate, HogRepository hogRepository) {
        this.restTemplate = restTemplate;
        this.hogRepository = hogRepository;
    }
	
    public List<HogsData> fetchHogsAndPigsReport(String month, String year) {
        // NASS Hogs & Pigs Inventory is published quarterly. The `month` query param is
        // NOT a real NASS filter — without `reference_period_desc` the API returns all four
        // quarterly snapshots in one response (4 rows per state). Convert the numeric month
        // to "FIRST OF MMM" so we only get the requested quarter.
        // Stripped down: commodity_desc=HOGS is already specific.
        // The old `sector` / `group` params were misnamed (real names are sector_desc / group_desc)
        // and NASS now 400s when it sees unknown query params.
        // IMPORTANT: build to a URI (not a String). RestTemplate.exchange(String, ...) treats
        // its argument as a URI TEMPLATE and re-encodes it, turning our already-encoded "%20"
        // into "%2520" — NASS then sees the literal text "FIRST%20OF%20JAN" and 400s with
        // "bad request - invalid query". Passing a URI bypasses the template processing.
        URI url = UriComponentsBuilder.fromHttpUrl("https://quickstats.nass.usda.gov/api/api_GET/")
                .queryParam("key", apiKey)
                .queryParam("commodity_desc", "HOGS")
                .queryParam("statisticcat_desc", "INVENTORY")
                .queryParam("agg_level_desc", "STATE")
                .queryParam("reference_period_desc", monthToFirstOf(month))
                .queryParam("year", year)
                .build()
                .encode()
                .toUri();

        System.out.println("[NASS HOGS v3] " + url);

        try {
            ResponseEntity<HogResponse> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<HogResponse>() {}
            );
            if (response.getBody() != null && response.getBody().getData() != null) {
                List<HogsData> data = response.getBody().getData();
                System.out.println("[NASS HOGS] received " + data.size() + " rows");
                return data;
            }
            System.out.println("[NASS HOGS] empty response");
            return Collections.emptyList();
        } catch (Exception e) {
            System.err.println("[NASS HOGS] error: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            return Collections.emptyList();
        }
    }

    public List<CattleData> fetchCattleOnFeedReport(String month, String year) {
     
        
        URI url = UriComponentsBuilder.fromHttpUrl("https://quickstats.nass.usda.gov/api/api_GET/")
                .queryParam("key", apiKey)
                .queryParam("commodity_desc", "CATTLE")
                .queryParam("statisticcat_desc", "INVENTORY")
                .queryParam("agg_level_desc", "STATE")
                .queryParam("reference_period_desc", monthToFirstOf(month))
                .queryParam("year", year)
                .build()
                .encode()
                .toUri();

        System.out.println("[NASS CATTLE v3] " + url);

        try {
            ResponseEntity<CattleResponse> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<CattleResponse>() {}
            );
            if (response.getBody() != null && response.getBody().getData() != null) {
                List<CattleData> data = response.getBody().getData();
                System.out.println("[NASS CATTLE] received " + data.size() + " rows");
                return data;
            }
            System.out.println("[NASS CATTLE] empty response");
            return Collections.emptyList();
        } catch (Exception e) {
            System.err.println("[NASS CATTLE] error: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * NASS uses "FIRST OF MMM" for the as-of date on inventory reports
     * (e.g. "FIRST OF JAN"). Convert a numeric month string to that format.
     */
    private static String monthToFirstOf(String month) {
        String[] abbr = { "JAN", "FEB", "MAR", "APR", "MAY", "JUN",
                          "JUL", "AUG", "SEP", "OCT", "NOV", "DEC" };
        try {
            int m = Integer.parseInt(month);
            if (m >= 1 && m <= 12) return "FIRST OF " + abbr[m - 1];
        } catch (NumberFormatException ignored) { /* fall through */ }
        return "FIRST OF JAN";
    }
}
	

