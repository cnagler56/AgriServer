package com.home.Service;

import java.net.URI;
import java.time.Year;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
 * USDA NASS weekly Broiler Hatchery — broiler-type chicks placed (national).
 * Released every Wednesday; we cache the latest week with week-over-week and
 * year-over-year change for the home-page news feed.
 */
@Service
public class BroilerService {

    @Value("${usda.api.key}")
    private String apiKey;

    private static final String SHORT_DESC = "CHICKENS, BROILERS - PLACEMENTS, MEASURED IN HEAD";

    private final RestTemplate restTemplate;
    private volatile Map<String, Object> latest = Map.of();

    public BroilerService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    private record Week(String weekEnding, String refPeriod, long head) {}

    /* ── scheduling ─────────────────────────────────────────────────────── */

    @EventListener(ApplicationReadyEvent.class)
    public void prewarm() {
        new Thread(() -> {
            try { refresh(); } catch (Exception e) {
                System.err.println("[BROILER] startup load failed: " + e.getMessage());
            }
        }, "broiler-prewarm").start();
    }

    /** Released Wednesdays; a daily check picks it up within a day of release. */
    @Scheduled(cron = "0 40 6 * * *", zone = "America/Chicago")
    public void scheduledRefresh() {
        try { refresh(); } catch (Exception e) {
            System.err.println("[BROILER] scheduled load failed: " + e.getMessage());
        }
    }

    /* ── load + parse ───────────────────────────────────────────────────── */

    public void refresh() {
        int thisYear = Year.now().getValue();
        URI url = UriComponentsBuilder.fromHttpUrl("https://quickstats.nass.usda.gov/api/api_GET/")
            .queryParam("key", apiKey)
            .queryParam("commodity_desc", "CHICKENS")
            .queryParam("short_desc", SHORT_DESC)
            .queryParam("agg_level_desc", "NATIONAL")
            .queryParam("freq_desc", "WEEKLY")
            .queryParam("year__GE", String.valueOf(thisYear - 1))
            .queryParam("format", "JSON")
            .build().encode().toUri();

        List<Map<String, Object>> rows = fetch(url);
        List<Week> weeks = rows.stream()
            .map(r -> new Week(str(r.get("week_ending")), str(r.get("reference_period_desc")), parseHead(r.get("Value"))))
            .filter(w -> w.weekEnding() != null && !w.weekEnding().isBlank() && w.head() > 0)
            .sorted(Comparator.comparing(Week::weekEnding).reversed())
            .toList();
        if (weeks.isEmpty()) { System.err.println("[BROILER] no rows"); return; }

        Week cur = weeks.get(0);
        Week prior = weeks.size() > 1 ? weeks.get(1) : null;              // week-over-week
        Week yearAgo = weeks.stream()                                     // same WEEK # last year
            .filter(w -> w.refPeriod() != null && w.refPeriod().equals(cur.refPeriod())
                && !w.weekEnding().equals(cur.weekEnding()))
            .findFirst().orElse(null);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("weekEnding", cur.weekEnding());
        out.put("refPeriod", cur.refPeriod());
        out.put("placements", cur.head());
        if (prior != null && prior.head() > 0)
            out.put("wowPct", Math.round((cur.head() - prior.head()) * 1000.0 / prior.head()) / 10.0);
        if (yearAgo != null && yearAgo.head() > 0)
            out.put("yoyPct", Math.round((cur.head() - yearAgo.head()) * 1000.0 / yearAgo.head()) / 10.0);
        latest = out;
        System.out.println("[BROILER] " + cur.weekEnding() + ": " + cur.head() + " head placed");
    }

    /* ── read ───────────────────────────────────────────────────────────── */

    public Map<String, Object> getLatest() { return latest; }

    /* ── helpers ────────────────────────────────────────────────────────── */

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fetch(URI url) {
        try {
            ResponseEntity<Map> resp = restTemplate.exchange(url, HttpMethod.GET, null, Map.class);
            Object data = resp.getBody() == null ? null : resp.getBody().get("data");
            return data instanceof List ? (List<Map<String, Object>>) data : Collections.emptyList();
        } catch (Exception e) {
            System.err.println("[BROILER] fetch failed: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            return Collections.emptyList();
        }
    }

    private static long parseHead(Object v) {
        if (v == null) return 0;
        try { return Long.parseLong(v.toString().replaceAll("[,\\s]", "")); } catch (Exception e) { return 0; }
    }
    private static String str(Object v) { return v == null ? null : v.toString(); }
}
