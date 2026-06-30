package com.home.Service;

import java.time.LocalDateTime;
import java.time.Year;
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

import com.fasterxml.jackson.databind.ObjectMapper;

import com.home.Domain.ExportSalesSnapshot;
import com.home.Repository.ExportSalesSnapshotRepository;

/**
 * USDA FAS weekly Export Sales (ESR) — the Thursday report markets watch closely:
 * net new sales, weekly shipments, and outstanding commitments by commodity, plus
 * the week's top destinations (China watch). Cached in memory, refreshed daily.
 * Needs a FAS Open Data key in FAS_API_KEY.
 */
@Service
public class ExportSalesService {

    private static final String BASE = "https://api.fas.usda.gov/api/esr";

    /** Site commodity → FAS commodity code + display unit. */
    private record Comm(int code, String unit) {}
    private static final Map<String, Comm> COMMODITIES = Map.of(
        "CORN",         new Comm(401,  "MT"),
        "SOYBEANS",     new Comm(801,  "MT"),
        "WHEAT",        new Comm(107,  "MT"),
        "SOYBEAN_MEAL", new Comm(901,  "MT"),
        "SOYBEAN_OIL",  new Comm(902,  "MT"),
        "COTTON",       new Comm(1404, "running bales"));

    private final RestTemplate restTemplate;

    @Value("${FAS_API_KEY:}")
    private String apiKey;

    private final ExportSalesSnapshotRepository snapshotRepo;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, Map<String, Object>> cache = new ConcurrentHashMap<>();
    private volatile Map<Integer, String> countryNames = Map.of();
    private volatile LocalDateTime updatedAt;

    public ExportSalesService(RestTemplate restTemplate, ExportSalesSnapshotRepository snapshotRepo) {
        this.restTemplate = restTemplate;
        this.snapshotRepo = snapshotRepo;
    }

    /* ── scheduling ─────────────────────────────────────────────────────── */

    @EventListener(ApplicationReadyEvent.class)
    public void prewarm() {
        // Serve last-good immediately from the DB, even if FAS is unreachable on boot.
        loadCacheFromDb();
        new Thread(() -> {
            try { refresh(); } catch (Exception e) {
                System.err.println("[ESR] startup load failed: " + e.getMessage());
            }
        }, "esr-prewarm").start();
    }

    /** ESR releases Thursday ~7:30 CT; a daily fetch keeps us current. */
    @Scheduled(cron = "0 50 7 * * *", zone = "America/Chicago")
    public void scheduledRefresh() {
        try { refresh(); } catch (Exception e) {
            System.err.println("[ESR] scheduled load failed: " + e.getMessage());
        }
    }

    public void refresh() {
        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("[ESR] FAS_API_KEY not set — skipping fetch");
            return;
        }
        if (countryNames.isEmpty()) loadCountries();
        int ok = 0;
        for (Map.Entry<String, Comm> e : COMMODITIES.entrySet()) {
            Map<String, Object> built = load(e.getKey(), e.getValue());
            // Only overwrite on a successful build — a failed/empty FAS fetch keeps the
            // last-good cache (and DB row) rather than blanking the panel.
            if (built != null) { cache.put(e.getKey(), built); persist(e.getKey(), built); ok++; }
        }
        if (ok > 0) updatedAt = LocalDateTime.now();
        System.out.println("[ESR] cached export sales for " + ok + "/" + COMMODITIES.size() + " commodities");
    }

    /** Upsert a commodity's last-good result into the DB. */
    private void persist(String commodity, Map<String, Object> built) {
        try {
            ExportSalesSnapshot snap = snapshotRepo.findByCommodity(commodity)
                .orElseGet(ExportSalesSnapshot::new);
            snap.setCommodity(commodity);
            snap.setPayloadJson(mapper.writeValueAsString(built));
            snapshotRepo.save(snap);
        } catch (Exception e) {
            System.err.println("[ESR] persist failed for " + commodity + ": " + e.getMessage());
        }
    }

    /** Warm the in-memory cache from the persisted last-good rows. */
    private void loadCacheFromDb() {
        try {
            LocalDateTime newest = null;
            for (ExportSalesSnapshot snap : snapshotRepo.findAll()) {
                Map<String, Object> built = deserialize(snap.getPayloadJson());
                if (built == null) continue;
                cache.put(snap.getCommodity(), built);
                if (snap.getUpdatedAt() != null && (newest == null || snap.getUpdatedAt().isAfter(newest)))
                    newest = snap.getUpdatedAt();
            }
            if (newest != null) updatedAt = newest;
        } catch (Exception e) {
            System.err.println("[ESR] DB warm failed: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> deserialize(String json) {
        if (json == null || json.isBlank()) return null;
        try { return mapper.readValue(json, LinkedHashMap.class); }
        catch (Exception e) { return null; }
    }

    /* ── load + parse ───────────────────────────────────────────────────── */

    @SuppressWarnings("unchecked")
    private Map<String, Object> load(String commodity, Comm comm) {
        // The active marketing year shifts mid-calendar (Sep for corn/soy, etc.),
        // so fetch the current and next MY and keep whichever has the newest week.
        int y = Year.now().getValue();
        List<Map<String, Object>> rows = pickLatest(fetchRows(comm.code(), y), fetchRows(comm.code(), y + 1));
        if (rows == null || rows.isEmpty()) return null;

        String latestWeek = rows.stream().map(r -> str(r.get("weekEndingDate"))).filter(s -> s != null)
            .max(String::compareTo).orElse(null);
        if (latestWeek == null) return null;
        List<Map<String, Object>> week = rows.stream()
            .filter(r -> latestWeek.equals(str(r.get("weekEndingDate")))).toList();

        double netSales = sum(week, "currentMYNetSales");
        double shipments = sum(week, "weeklyExports");
        double commitment = sum(week, "currentMYTotalCommitment");
        double nextMY = sum(week, "nextMYNetSales");

        // Top destinations by net sales this week.
        List<Map<String, Object>> dests = new ArrayList<>();
        week.stream()
            .filter(r -> num(r.get("currentMYNetSales")) > 0)
            .sorted((a, b) -> Double.compare(num(b.get("currentMYNetSales")), num(a.get("currentMYNetSales"))))
            .limit(5)
            .forEach(r -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("country", countryNames.getOrDefault(intg(r.get("countryCode")), "—"));
                m.put("netSales", Math.round(num(r.get("currentMYNetSales"))));
                dests.add(m);
            });

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("commodity", commodity);
        out.put("unit", comm.unit());
        out.put("weekEnding", latestWeek.length() >= 10 ? latestWeek.substring(0, 10) : latestWeek);
        out.put("netSales", Math.round(netSales));
        out.put("shipments", Math.round(shipments));
        out.put("totalCommitment", Math.round(commitment));
        out.put("nextMYNetSales", Math.round(nextMY));
        out.put("topDestinations", dests);
        return out;
    }

    /** Of two market-year datasets, return the one with the most recent week. */
    private List<Map<String, Object>> pickLatest(List<Map<String, Object>> a, List<Map<String, Object>> b) {
        String wa = latestWeekOf(a), wb = latestWeekOf(b);
        if (wa == null) return b;
        if (wb == null) return a;
        return wb.compareTo(wa) > 0 ? b : a;
    }
    private String latestWeekOf(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) return null;
        return rows.stream().map(r -> str(r.get("weekEndingDate"))).filter(s -> s != null).max(String::compareTo).orElse(null);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fetchRows(int code, int marketYear) {
        try {
            String url = BASE + "/exports/commodityCode/" + code + "/allCountries/marketYear/" + marketYear;
            ResponseEntity<List> resp = restTemplate.exchange(url, HttpMethod.GET, authEntity(), List.class);
            Object body = resp.getBody();
            return body instanceof List ? (List<Map<String, Object>>) body : Collections.emptyList();
        } catch (Exception e) {
            return Collections.emptyList();   // future/empty market years 4xx — fine
        }
    }

    @SuppressWarnings("unchecked")
    private void loadCountries() {
        try {
            ResponseEntity<List> resp = restTemplate.exchange(BASE + "/countries", HttpMethod.GET, authEntity(), List.class);
            List<Map<String, Object>> list = resp.getBody();
            if (list == null) return;
            Map<Integer, String> m = new ConcurrentHashMap<>();
            for (Map<String, Object> c : list) {
                Integer cc = intg(c.get("countryCode"));
                String name = str(c.get("countryName"));
                if (cc != null && name != null) m.put(cc, titleCase(name.trim()));
            }
            countryNames = m;
        } catch (Exception e) {
            System.err.println("[ESR] country list failed: " + e.getMessage());
        }
    }

    private HttpEntity<Void> authEntity() {
        HttpHeaders h = new HttpHeaders();
        h.set("X-Api-Key", apiKey);
        h.set("User-Agent", "just4ag/1.0 (export sales)");
        return new HttpEntity<>(h);
    }

    /* ── read ───────────────────────────────────────────────────────────── */

    public Map<String, Object> getExportSales(String commodity) {
        String key = commodity == null ? "" : commodity.toUpperCase();
        Map<String, Object> cached = cache.get(key);
        if (cached == null) {
            // Cold cache (e.g. a request lands before prewarm finishes) — fall back to the DB.
            ExportSalesSnapshot snap = snapshotRepo.findByCommodity(key).orElse(null);
            Map<String, Object> fromDb = snap == null ? null : deserialize(snap.getPayloadJson());
            if (fromDb != null) {
                cache.put(key, fromDb);
                cached = fromDb;
                if (updatedAt == null) updatedAt = snap.getUpdatedAt();
            }
        }
        if (cached != null) {
            Map<String, Object> out = new LinkedHashMap<>(cached);
            out.put("source", "USDA FAS Export Sales");
            out.put("updatedAt", updatedAt == null ? null : updatedAt.toString());
            return out;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("commodity", key);
        out.put("message", COMMODITIES.containsKey(key) ? "Export sales not loaded yet." : "Not tracked.");
        return out;
    }

    /* ── helpers ────────────────────────────────────────────────────────── */

    private static double sum(List<Map<String, Object>> rows, String field) {
        double s = 0;
        for (Map<String, Object> r : rows) s += num(r.get(field));
        return s;
    }
    private static String titleCase(String s) {
        if (s.isEmpty()) return s;
        String lower = s.toLowerCase();
        StringBuilder b = new StringBuilder();
        boolean up = true;
        for (char c : lower.toCharArray()) {
            b.append(up && Character.isLetter(c) ? Character.toUpperCase(c) : c);
            up = !Character.isLetter(c);
        }
        return b.toString();
    }
    private static double num(Object o) {
        if (o == null) return 0;
        if (o instanceof Number) return ((Number) o).doubleValue();
        try { return Double.parseDouble(o.toString()); } catch (Exception e) { return 0; }
    }
    private static Integer intg(Object o) {
        if (o == null) return null;
        if (o instanceof Number) return ((Number) o).intValue();
        try { return Integer.parseInt(o.toString().trim()); } catch (Exception e) { return null; }
    }
    private static String str(Object o) { return o == null ? null : o.toString(); }
}
