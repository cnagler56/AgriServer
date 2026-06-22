package com.home.Service;

import java.net.URI;
import java.time.Year;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    /* ── National headline reports ──────────────────────────────────────── */

    /**
     * Monthly "Cattle on Feed" — feedlots with 1,000+ head capacity, the watched
     * series (NASS also carries a semiannual all-feedlots total we ignore here).
     * Returns the latest month plus the year-over-year change for that month.
     */
    public Map<String, Object> getCattleOnFeed() {
        int thisYear = Year.now().getValue();
        URI url = UriComponentsBuilder.fromHttpUrl("https://quickstats.nass.usda.gov/api/api_GET/")
                .queryParam("key", apiKey)
                .queryParam("short_desc", "CATTLE, ON FEED - INVENTORY")
                .queryParam("agg_level_desc", "NATIONAL")
                .queryParam("year__GE", String.valueOf(thisYear - 2))
                .queryParam("format", "JSON")
                .build().encode().toUri();

        List<Map<String, Object>> rows = fetchNass(url).stream()
                .filter(r -> str(r.get("domaincat_desc")).contains("1,000 OR MORE"))
                .sorted(byPeriodDesc())
                .toList();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("report", "Cattle on Feed");
        out.put("unit", "head");
        if (rows.isEmpty()) { out.put("message", "Cattle on Feed report not available yet."); return out; }

        Map<String, Object> latest = rows.get(0);
        String period = str(latest.get("reference_period_desc"));
        Integer year = intg(latest.get("year"));
        Long value = parseHead(latest.get("Value"));
        out.put("period", period);
        out.put("year", year);
        out.put("value", value);
        // YoY = same reference period, prior year.
        Long prior = rows.stream()
                .filter(r -> period.equals(str(r.get("reference_period_desc"))) && year != null
                        && Integer.valueOf(year - 1).equals(intg(r.get("year"))))
                .map(r -> parseHead(r.get("Value"))).filter(v -> v != null).findFirst().orElse(null);
        if (value != null && prior != null && prior != 0) {
            out.put("yoyPct", Math.round((value - prior) * 1000.0 / prior) / 10.0);
        }
        return out;
    }

    /**
     * Quarterly "Hogs & Pigs" — the latest national inventory split into all hogs,
     * the breeding herd, and market hogs, plus YoY on the total.
     */
    public Map<String, Object> getHogsAndPigs() {
        int thisYear = Year.now().getValue();
        URI url = UriComponentsBuilder.fromHttpUrl("https://quickstats.nass.usda.gov/api/api_GET/")
                .queryParam("key", apiKey)
                .queryParam("commodity_desc", "HOGS")
                .queryParam("statisticcat_desc", "INVENTORY")
                .queryParam("agg_level_desc", "NATIONAL")
                .queryParam("domain_desc", "TOTAL")
                .queryParam("year__GE", String.valueOf(thisYear - 2))
                .queryParam("format", "JSON")
                .build().encode().toUri();

        List<Map<String, Object>> all = fetchNass(url);
        List<Map<String, Object>> totalHogs = all.stream()
                .filter(r -> "HOGS - INVENTORY".equals(str(r.get("short_desc"))))
                .sorted(byPeriodDesc()).toList();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("report", "Hogs & Pigs");
        out.put("unit", "head");
        if (totalHogs.isEmpty()) { out.put("message", "Hogs & Pigs report not available yet."); return out; }

        Map<String, Object> latest = totalHogs.get(0);
        String period = str(latest.get("reference_period_desc"));
        Integer year = intg(latest.get("year"));
        out.put("period", period);
        out.put("year", year);
        out.put("all", parseHead(latest.get("Value")));
        out.put("breeding", valueAt(all, "HOGS, BREEDING - INVENTORY", period, year));
        out.put("market", valueAt(all, "HOGS, MARKET - INVENTORY", period, year));

        Long total = parseHead(latest.get("Value"));
        Long prior = totalHogs.stream()
                .filter(r -> period.equals(str(r.get("reference_period_desc"))) && year != null
                        && Integer.valueOf(year - 1).equals(intg(r.get("year"))))
                .map(r -> parseHead(r.get("Value"))).filter(v -> v != null).findFirst().orElse(null);
        if (total != null && prior != null && prior != 0) {
            out.put("yoyPct", Math.round((total - prior) * 1000.0 / prior) / 10.0);
        }
        return out;
    }

    private Long valueAt(List<Map<String, Object>> rows, String shortDesc, String period, Integer year) {
        return rows.stream()
                .filter(r -> shortDesc.equals(str(r.get("short_desc")))
                        && period.equals(str(r.get("reference_period_desc")))
                        && (year == null || year.equals(intg(r.get("year")))))
                .map(r -> parseHead(r.get("Value"))).filter(v -> v != null).findFirst().orElse(null);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fetchNass(URI url) {
        try {
            ResponseEntity<Map> resp = restTemplate.exchange(url, HttpMethod.GET, null, Map.class);
            Object data = resp.getBody() == null ? null : resp.getBody().get("data");
            return data instanceof List ? (List<Map<String, Object>>) data : Collections.emptyList();
        } catch (Exception e) {
            System.err.println("[NASS] fetch failed: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /** Sort newest first by (year, month within "FIRST OF MMM"). */
    private static Comparator<Map<String, Object>> byPeriodDesc() {
        return Comparator
            .comparing((Map<String, Object> r) -> intg(r.get("year")) == null ? 0 : intg(r.get("year")))
            .thenComparing(r -> monthIndex(str(r.get("reference_period_desc"))))
            .reversed();
    }

    private static final String[] ABBR = { "JAN", "FEB", "MAR", "APR", "MAY", "JUN",
                                            "JUL", "AUG", "SEP", "OCT", "NOV", "DEC" };
    private static int monthIndex(String period) {
        if (period == null) return 0;
        String p = period.toUpperCase();
        for (int i = 0; i < ABBR.length; i++) if (p.contains(ABBR[i])) return i + 1;
        return 0;
    }
    private static Long parseHead(Object v) {
        if (v == null) return null;
        try { return Long.parseLong(v.toString().replaceAll("[,\\s]", "")); } catch (Exception e) { return null; }
    }
    private static Integer intg(Object v) {
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).intValue();
        try { return Integer.parseInt(v.toString().trim()); } catch (Exception e) { return null; }
    }
    private static String str(Object v) { return v == null ? "" : v.toString(); }

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
	

