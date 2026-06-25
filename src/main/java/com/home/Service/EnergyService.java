package com.home.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * Energy & input-cost data from the EIA API, for an ag audience:
 *   - Weekly petroleum: WTI crude, diesel demand, retail diesel price, propane
 *     stocks (fuel + input costs that hit the farm).
 *   - Monthly biofuel feedstock: soybean oil consumed by biodiesel & renewable
 *     diesel plants — a direct read on soybean-oil demand.
 *
 * Series are cached in memory and refreshed daily (EIA updates weekly/monthly).
 * Needs a free EIA key in EIA_API_KEY (shared with the ethanol tracker).
 */
@Service
public class EnergyService {

    private static final String BASE = "https://api.eia.gov/v2/seriesid/";

    /** A tracked series: cache key, EIA series id, display unit, label. */
    public record Series(String key, String id, String unit, String label) {}

    /** Weekly petroleum & gas — fuel & input costs. */
    private static final List<Series> PETRO = List.of(
        new Series("crude",        "PET.RWTC.W",                       "$/bbl",   "WTI Crude Oil"),
        new Series("dieselDemand", "PET.WDIUPUS2.W",                   "MBBL/D",  "Diesel Demand"),
        new Series("dieselPrice",  "PET.EMD_EPD2D_PTE_NUS_DPG.W",      "$/gal",   "Retail Diesel"),
        new Series("propaneStocks","PET.WPRSTUS1.W",                   "MBBL",    "Propane Stocks"),
        // Henry Hub natural gas — the main cost driver for nitrogen fertilizer.
        new Series("naturalGas",   "NG.RNGWHHD.W",                     "$/MMBtu", "Natural Gas (Henry Hub)"));

    /** Monthly soybean-oil consumed for biofuels (million pounds). */
    private static final List<Series> SOYOIL = List.of(
        new Series("biodiesel",       "PET.M_EPOOBDSOD_YIFBP_NUS_MMLB.M", "MMlb", "Biodiesel"),
        new Series("renewableDiesel", "PET.M_EPOOBDSOR_YIFBP_NUS_MMLB.M", "MMlb", "Renewable Diesel"));

    private final RestTemplate restTemplate;

    @Value("${EIA_API_KEY:}")
    private String apiKey;

    private final Map<String, List<Point>> cache = new ConcurrentHashMap<>(); // oldest → newest
    private volatile LocalDateTime updatedAt;

    public EnergyService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    private record Point(String period, double value) {}

    /* ── scheduling ─────────────────────────────────────────────────────── */

    @EventListener(ApplicationReadyEvent.class)
    public void prewarm() {
        new Thread(() -> {
            try { refresh(); } catch (Exception e) {
                System.err.println("[ENERGY] startup load failed: " + e.getMessage());
            }
        }, "energy-prewarm").start();
    }

    @Scheduled(cron = "0 20 10 * * *", zone = "America/Chicago")
    public void scheduledRefresh() {
        try { refresh(); } catch (Exception e) {
            System.err.println("[ENERGY] scheduled load failed: " + e.getMessage());
        }
    }

    public void refresh() {
        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("[ENERGY] EIA_API_KEY not set — skipping fetch");
            return;
        }
        int loaded = 0;
        for (Series s : allSeries()) {
            List<Point> pts = fetchSeries(s.id());
            if (!pts.isEmpty()) { cache.put(s.key(), pts); loaded++; }
        }
        if (loaded > 0) updatedAt = LocalDateTime.now();
        System.out.println("[ENERGY] cached " + loaded + "/" + allSeries().size() + " series");
    }

    private static List<Series> allSeries() {
        List<Series> all = new ArrayList<>(PETRO);
        all.addAll(SOYOIL);
        return all;
    }

    /* ── read for the API ───────────────────────────────────────────────── */

    /** Weekly petroleum metrics: each with latest, week-over-week, and a chart series. */
    public Map<String, Object> getEnergy() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("source", "U.S. Energy Information Administration (EIA)");
        out.put("updatedAt", updatedAt == null ? null : updatedAt.toString());
        List<Map<String, Object>> metrics = new ArrayList<>();
        for (Series s : PETRO) {
            List<Point> pts = cache.get(s.key());
            if (pts == null || pts.isEmpty()) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("key", s.key());
            m.put("label", s.label());
            m.put("unit", s.unit());
            m.put("latest", last(pts));
            Double wow = (last(pts) != null && prev(pts) != null) ? round(last(pts) - prev(pts), 2) : null;
            m.put("wow", wow);
            m.put("asOf", pts.get(pts.size() - 1).period());
            m.put("series", tail(pts, 52));
            metrics.add(m);
        }
        out.put("metrics", metrics);
        return out;
    }

    /** Soybean oil consumed by biodiesel + renewable diesel, with combined totals. */
    public Map<String, Object> getSoyOilBiofuel() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("source", "U.S. Energy Information Administration (EIA)");
        out.put("updatedAt", updatedAt == null ? null : updatedAt.toString());
        out.put("unit", "million lbs");

        List<Point> bd = cache.getOrDefault("biodiesel", List.of());
        List<Point> rd = cache.getOrDefault("renewableDiesel", List.of());
        if (bd.isEmpty() && rd.isEmpty()) {
            out.put("message", "Biofuel feedstock data isn't loaded yet.");
            return out;
        }
        out.put("biodiesel", tail(bd, 60));
        out.put("renewableDiesel", tail(rd, 60));
        out.put("biodieselLatest", last(bd));
        out.put("renewableDieselLatest", last(rd));

        // Combined total per period (aligned by month), for the headline + chart.
        Map<String, Double> byPeriod = new java.util.TreeMap<>();
        for (Point p : bd) byPeriod.merge(p.period(), p.value(), Double::sum);
        for (Point p : rd) byPeriod.merge(p.period(), p.value(), Double::sum);
        List<Point> total = byPeriod.entrySet().stream().map(e -> new Point(e.getKey(), e.getValue())).toList();
        out.put("total", tail(total, 60));
        Double tLatest = last(total);
        out.put("totalLatest", tLatest);
        out.put("asOf", total.isEmpty() ? null : total.get(total.size() - 1).period());
        if (tLatest != null && prev(total) != null && prev(total) != 0)
            out.put("totalMoMPct", round((tLatest - prev(total)) / prev(total) * 100, 1));
        Double yearAgo = total.size() >= 13 ? total.get(total.size() - 13).value() : null;
        if (tLatest != null && yearAgo != null && yearAgo != 0)
            out.put("totalYoYPct", round((tLatest - yearAgo) / yearAgo * 100, 1));
        return out;
    }

    /* ── fetch + helpers ────────────────────────────────────────────────── */

    @SuppressWarnings("unchecked")
    private List<Point> fetchSeries(String seriesId) {
        try {
            String url = BASE + seriesId + "?api_key=" + apiKey + "&length=60";
            HttpHeaders h = new HttpHeaders();
            h.set("User-Agent", "just4ag/1.0 (energy tracker)");
            ResponseEntity<Map> resp = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(h), Map.class);
            if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
                System.err.println("[ENERGY] " + seriesId + " -> HTTP " + resp.getStatusCode());
                return List.of();
            }
            Map<String, Object> response = (Map<String, Object>) resp.getBody().get("response");
            List<Map<String, Object>> data = response == null ? null : (List<Map<String, Object>>) response.get("data");
            if (data == null) return List.of();
            List<Point> out = new ArrayList<>();
            for (Map<String, Object> row : data) {
                String period = row.get("period") == null ? null : row.get("period").toString();
                Double value = num(row.get("value"));
                if (period != null && value != null) out.add(new Point(period, value));
            }
            Collections.reverse(out);   // EIA returns newest-first
            return out;
        } catch (Exception e) {
            System.err.println("[ENERGY] fetch " + seriesId + " failed: "
                + e.getClass().getSimpleName() + " " + e.getMessage());
            return List.of();
        }
    }

    private static List<Map<String, Object>> tail(List<Point> pts, int n) {
        List<Map<String, Object>> out = new ArrayList<>();
        int from = Math.max(0, pts.size() - n);
        for (Point p : pts.subList(from, pts.size())) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("period", p.period());
            m.put("value", p.value());
            out.add(m);
        }
        return out;
    }
    private static Double last(List<Point> p) { return p.isEmpty() ? null : p.get(p.size() - 1).value(); }
    private static Double prev(List<Point> p) { return p.size() < 2 ? null : p.get(p.size() - 2).value(); }
    private static Double num(Object o) {
        if (o == null) return null;
        if (o instanceof Number) return ((Number) o).doubleValue();
        try { return Double.parseDouble(o.toString()); } catch (Exception e) { return null; }
    }
    private static double round(double v, int places) {
        double f = Math.pow(10, places);
        return Math.round(v * f) / f;
    }
}
