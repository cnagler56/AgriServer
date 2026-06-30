package com.home.Service;

import java.net.URI;
import java.time.Year;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

import com.fasterxml.jackson.databind.ObjectMapper;

import com.home.Domain.GrainStocksSnapshot;
import com.home.Repository.GrainStocksSnapshotRepository;

/**
 * USDA NASS quarterly Grain Stocks (Jan / Mar / Jun / Sep) — on-farm and off-farm
 * stocks of corn, soybeans, and wheat, the demand-side counterpart to production.
 * Cached in memory; refreshed daily (data only changes quarterly).
 */
@Service
public class GrainStocksService {

    @Value("${usda.api.key}")
    private String apiKey;

    private static final List<String> COMMODITIES = List.of("CORN", "SOYBEANS", "WHEAT");

    /** Quarter order within a calendar year for "is this period newer?". */
    private static final Map<String, Integer> Q_RANK = Map.of(
        "FIRST OF MAR", 1, "FIRST OF JUN", 2, "FIRST OF SEP", 3, "FIRST OF DEC", 4);

    private final RestTemplate restTemplate;
    private final GrainStocksSnapshotRepository snapshotRepo;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, Map<String, Object>> cache = new ConcurrentHashMap<>();

    public GrainStocksService(RestTemplate restTemplate, GrainStocksSnapshotRepository snapshotRepo) {
        this.restTemplate = restTemplate;
        this.snapshotRepo = snapshotRepo;
    }

    /* ── scheduling ─────────────────────────────────────────────────────── */

    @EventListener(ApplicationReadyEvent.class)
    public void prewarm() {
        // Serve last-good immediately from the DB, even if USDA is unreachable on boot.
        loadCacheFromDb();
        new Thread(() -> {
            try { refresh(); } catch (Exception e) {
                System.err.println("[STOCKS] startup load failed: " + e.getMessage());
            }
        }, "stocks-prewarm").start();
    }

    @Scheduled(cron = "0 35 6 * * *", zone = "America/Chicago")
    public void scheduledRefresh() {
        try { refresh(); } catch (Exception e) {
            System.err.println("[STOCKS] scheduled load failed: " + e.getMessage());
        }
    }

    public void refresh() {
        for (String c : COMMODITIES) {
            Map<String, Object> built = load(c);
            // Only overwrite on a successful build — a failed/empty USDA fetch leaves
            // the last-good cache (and DB row) in place instead of blanking the panel.
            if (built != null) {
                cache.put(c, built);
                persist(c, built);
            }
        }
    }

    /** Upsert a commodity's last-good result into the DB. */
    private void persist(String commodity, Map<String, Object> built) {
        try {
            GrainStocksSnapshot snap = snapshotRepo.findByCommodity(commodity)
                .orElseGet(GrainStocksSnapshot::new);
            snap.setCommodity(commodity);
            snap.setPayloadJson(mapper.writeValueAsString(built));
            snapshotRepo.save(snap);
        } catch (Exception e) {
            System.err.println("[STOCKS] persist failed for " + commodity + ": " + e.getMessage());
        }
    }

    /** Warm the in-memory cache from the persisted last-good rows. */
    private void loadCacheFromDb() {
        try {
            for (GrainStocksSnapshot snap : snapshotRepo.findAll()) {
                Map<String, Object> built = deserialize(snap.getPayloadJson());
                if (built != null) cache.put(snap.getCommodity(), built);
            }
        } catch (Exception e) {
            System.err.println("[STOCKS] DB warm failed: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> deserialize(String json) {
        if (json == null || json.isBlank()) return null;
        try { return mapper.readValue(json, LinkedHashMap.class); }
        catch (Exception e) { return null; }
    }

    /* ── load + parse ───────────────────────────────────────────────────── */

    private Map<String, Object> load(String commodity) {
        int thisYear = Year.now().getValue();
        URI url = UriComponentsBuilder.fromHttpUrl("https://quickstats.nass.usda.gov/api/api_GET/")
            .queryParam("key", apiKey)
            .queryParam("commodity_desc", commodity)
            .queryParam("statisticcat_desc", "STOCKS")
            .queryParam("agg_level_desc", "NATIONAL")
            .queryParam("unit_desc", "BU")
            .queryParam("year__GE", String.valueOf(thisYear - 2))
            .queryParam("format", "JSON")
            .build().encode().toUri();

        List<Map<String, Object>> rows = fetchNass(url);
        if (rows.isEmpty()) { System.err.println("[STOCKS] no rows for " + commodity); return null; }

        // Newest (year, quarter) present.
        Map<String, Object> newest = rows.stream()
            .filter(r -> Q_RANK.containsKey(str(r.get("reference_period_desc"))))
            .max((a, b) -> {
                int ya = intg(a.get("year")), yb = intg(b.get("year"));
                if (ya != yb) return Integer.compare(ya, yb);
                return Integer.compare(rank(a), rank(b));
            }).orElse(null);
        if (newest == null) return null;

        int year = intg(newest.get("year"));
        String period = str(newest.get("reference_period_desc"));

        Long total = pick(rows, commodity, year, period, "TOTAL");
        Long onFarm = pick(rows, commodity, year, period, "ON FARM");
        Long offFarm = pick(rows, commodity, year, period, "OFF FARM");
        Long priorTotal = pick(rows, commodity, year - 1, period, "TOTAL");

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("commodity", commodity);
        out.put("unit", "bushels");
        out.put("period", prettyPeriod(period, year));
        out.put("year", year);
        out.put("total", total);
        out.put("onFarm", onFarm);
        out.put("offFarm", offFarm);
        if (total != null && priorTotal != null && priorTotal != 0)
            out.put("yoyPct", Math.round((total - priorTotal) * 1000.0 / priorTotal) / 10.0);
        System.out.println("[STOCKS] " + commodity + " " + period + " " + year + " = " + total);
        return out;
    }

    /** Total / on-farm / off-farm value for a commodity+year+quarter, matched on short_desc. */
    private Long pick(List<Map<String, Object>> rows, String commodity, int year, String period, String position) {
        for (Map<String, Object> r : rows) {
            if (intg(r.get("year")) != year) continue;
            if (!period.equals(str(r.get("reference_period_desc")))) continue;
            String sd = str(r.get("short_desc"));
            if (sd == null) continue;
            boolean on = sd.contains("ON FARM"), off = sd.contains("OFF FARM");
            boolean match = switch (position) {
                case "ON FARM" -> on;
                case "OFF FARM" -> off;
                default -> !on && !off;      // total
            };
            if (match) return parseBu(r.get("Value"));
        }
        return null;
    }

    /* ── read ───────────────────────────────────────────────────────────── */

    public Map<String, Object> getStocks(String commodity) {
        String key = commodity == null ? "" : commodity.toUpperCase();
        Map<String, Object> cached = cache.get(key);
        if (cached == null) {
            // Cold cache (e.g. a request lands before prewarm finishes) — fall back to the DB.
            Map<String, Object> fromDb = snapshotRepo.findByCommodity(key)
                .map(s -> deserialize(s.getPayloadJson())).orElse(null);
            if (fromDb != null) { cache.put(key, fromDb); cached = fromDb; }
        }
        if (cached != null) return new LinkedHashMap<>(cached);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("commodity", key);
        out.put("message", COMMODITIES.contains(key) ? "Grain stocks not loaded yet." : "Not tracked.");
        return out;
    }

    /* ── helpers ────────────────────────────────────────────────────────── */

    private static String prettyPeriod(String refPeriod, int year) {
        String m = switch (refPeriod) {
            case "FIRST OF MAR" -> "Mar 1";
            case "FIRST OF JUN" -> "Jun 1";
            case "FIRST OF SEP" -> "Sep 1";
            case "FIRST OF DEC" -> "Dec 1";
            default -> refPeriod;
        };
        return m + ", " + year;
    }
    private static int rank(Map<String, Object> r) { return Q_RANK.getOrDefault(str(r.get("reference_period_desc")), 0); }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fetchNass(URI url) {
        // NASS can be flaky under report-day load; retry a few times before giving up.
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                ResponseEntity<Map> resp = restTemplate.exchange(url, HttpMethod.GET, null, Map.class);
                Object data = resp.getBody() == null ? null : resp.getBody().get("data");
                return data instanceof List ? (List<Map<String, Object>>) data : Collections.emptyList();
            } catch (Exception e) {
                System.err.println("[STOCKS] fetch attempt " + attempt + " failed: "
                    + e.getClass().getSimpleName() + " - " + e.getMessage());
                if (attempt < 3) {
                    try { Thread.sleep(1500L * attempt); }
                    catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
                }
            }
        }
        return Collections.emptyList();
    }
    private static Long parseBu(Object v) {
        if (v == null) return null;
        try { return Long.parseLong(v.toString().replaceAll("[,\\s]", "")); } catch (Exception e) { return null; }
    }
    private static int intg(Object o) {
        if (o == null) return -1;
        if (o instanceof Number) return ((Number) o).intValue();
        try { return Integer.parseInt(o.toString().trim()); } catch (Exception e) { return -1; }
    }
    private static String str(Object o) { return o == null ? null : o.toString(); }
}
