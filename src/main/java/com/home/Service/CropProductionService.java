package com.home.Service;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * County-level crop production (bushels) from USDA NASS Quick Stats, used to
 * shade a "where it's grown" overlay on the weather forecast map. One national
 * SURVEY query per crop (~2,500 county rows) keyed by 5-digit FIPS, cached in
 * memory. County figures are released annually, so a daily refresh is plenty.
 */
@Service
public class CropProductionService {

    @Value("${usda.api.key}")
    private String apiKey;

    private final RestTemplate restTemplate;

    /**
     * Site commodity code → NASS short_desc(s) for grain production in bushels.
     * Wheat has no single "all wheat" county series, so we sum the classes
     * (winter + spring excl. durum + durum) per county.
     */
    private static final Map<String, List<String>> SHORT_DESC = Map.of(
        "CORN", List.of("CORN, GRAIN - PRODUCTION, MEASURED IN BU"),
        "SOYBEANS", List.of("SOYBEANS - PRODUCTION, MEASURED IN BU"),
        "WHEAT", List.of(
            "WHEAT, WINTER - PRODUCTION, MEASURED IN BU",
            "WHEAT, SPRING, (EXCL DURUM) - PRODUCTION, MEASURED IN BU",
            "WHEAT, SPRING, DURUM - PRODUCTION, MEASURED IN BU"));

    /** commodity → cached result ({year, byFips, ...}). */
    private final Map<String, Map<String, Object>> cache = new ConcurrentHashMap<>();
    private volatile LocalDateTime updatedAt;

    public CropProductionService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /* ── scheduling ─────────────────────────────────────────────────────── */

    @EventListener(ApplicationReadyEvent.class)
    public void prewarm() {
        new Thread(() -> {
            try { refresh(); } catch (Exception e) {
                System.err.println("[CROPPROD] startup load failed: " + e.getMessage());
            }
        }, "cropprod-prewarm").start();
    }

    @Scheduled(cron = "0 30 6 * * *", zone = "America/Chicago")
    public void scheduledRefresh() {
        try { refresh(); } catch (Exception e) {
            System.err.println("[CROPPROD] scheduled load failed: " + e.getMessage());
        }
    }

    public void refresh() {
        for (String commodity : SHORT_DESC.keySet()) {
            Map<String, Object> built = load(commodity);
            if (built != null) cache.put(commodity, built);
        }
        updatedAt = LocalDateTime.now();
    }

    /* ── load + parse ───────────────────────────────────────────────────── */

    private Map<String, Object> load(String commodity) {
        int thisYear = Year.now().getValue();
        List<Map<String, Object>> rows = new java.util.ArrayList<>();
        for (String shortDesc : SHORT_DESC.get(commodity)) {
            URI url = UriComponentsBuilder.fromHttpUrl("https://quickstats.nass.usda.gov/api/api_GET/")
                .queryParam("key", apiKey)
                .queryParam("source_desc", "SURVEY")
                .queryParam("agg_level_desc", "COUNTY")
                .queryParam("short_desc", shortDesc)
                .queryParam("year__GE", String.valueOf(thisYear - 2))
                .queryParam("format", "JSON")
                .build().encode().toUri();
            rows.addAll(fetchNass(url));
        }
        if (rows.isEmpty()) { System.err.println("[CROPPROD] no rows for " + commodity); return null; }

        // NASS releases county estimates state-by-state, so a single global "latest
        // year" silently drops every county whose newest year isn't out yet (e.g. big
        // corn counties still on 2024 while neighbours have 2025). Instead, key each
        // county to ITS most recent year with real data, then sum that year's rows
        // (for wheat, the winter/spring/durum classes within that year).
        Map<String, Integer> latestYearByFips = new LinkedHashMap<>();
        for (Map<String, Object> r : rows) {
            String fips = fipsOf(r);
            if (fips == null || parseVal(r.get("Value")) == null) continue;
            Integer y = intg(r.get("year"));
            if (y == null) continue;
            latestYearByFips.merge(fips, y, Math::max);
        }

        Map<String, Long> byFips = new LinkedHashMap<>();
        for (Map<String, Object> r : rows) {
            String fips = fipsOf(r);
            if (fips == null) continue;
            Integer y = intg(r.get("year"));
            if (y == null || !y.equals(latestYearByFips.get(fips))) continue;
            Long v = parseVal(r.get("Value"));
            if (v == null) continue;                              // withheld "(D)" etc.
            byFips.merge(fips, v, Long::sum);
        }

        int maxYear = latestYearByFips.values().stream().mapToInt(Integer::intValue).max().orElse(thisYear);
        int minYear = latestYearByFips.values().stream().mapToInt(Integer::intValue).min().orElse(maxYear);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("commodity", commodity);
        out.put("year", maxYear);
        out.put("minYear", minYear);                              // map may mix years per county
        out.put("unit", "bushels");
        out.put("source", "USDA NASS Quick Stats");
        out.put("counties", byFips.size());
        out.put("byFips", byFips);
        System.out.println("[CROPPROD] " + commodity + " " + minYear + "-" + maxYear + ": " + byFips.size() + " counties");
        return out;
    }

    /* ── read for the API ───────────────────────────────────────────────── */

    public Map<String, Object> getProduction(String commodity) {
        String key = commodity == null ? "" : commodity.toUpperCase();
        Map<String, Object> cached = cache.get(key);
        if (cached != null) {
            Map<String, Object> out = new LinkedHashMap<>(cached);
            out.put("updatedAt", updatedAt == null ? null : updatedAt.toString());
            return out;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("commodity", key);
        if (!SHORT_DESC.containsKey(key)) out.put("message", "Not tracked.");
        else out.put("message", "No data loaded yet.");
        out.put("byFips", Collections.emptyMap());
        return out;
    }

    /* ── helpers ────────────────────────────────────────────────────────── */

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fetchNass(URI url) {
        try {
            ResponseEntity<Map> resp = restTemplate.exchange(url, HttpMethod.GET, null, Map.class);
            Object data = resp.getBody() == null ? null : resp.getBody().get("data");
            return data instanceof List ? (List<Map<String, Object>>) data : Collections.emptyList();
        } catch (Exception e) {
            System.err.println("[CROPPROD] fetch failed: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /** 5-digit FIPS (state + county) for a row, or null for state/district aggregates. */
    private static String fipsOf(Map<String, Object> r) {
        String sf = str(r.get("state_fips_code"));
        String cf = str(r.get("county_ansi"));
        if (sf.isBlank() || cf.isBlank()) return null;
        return pad(sf, 2) + pad(cf, 3);
    }

    private static String pad(String s, int len) {
        s = s.trim();
        while (s.length() < len) s = "0" + s;
        return s;
    }
    private static Long parseVal(Object v) {
        if (v == null) return null;
        try { return Long.parseLong(v.toString().replaceAll("[,\\s]", "")); } catch (Exception e) { return null; }
    }
    private static Integer intg(Object v) {
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).intValue();
        try { return Integer.parseInt(v.toString().trim()); } catch (Exception e) { return null; }
    }
    private static String str(Object v) { return v == null ? "" : v.toString(); }
}
