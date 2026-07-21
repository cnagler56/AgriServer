package com.home.Service;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import com.home.Domain.OutlookSnapshot;
import com.home.Repository.OutlookSnapshotRepository;

/**
 * Samples NOAA CPC's 6–10 and 8–14 day probability outlooks at Midwest state
 * centroids each day and computes a warmer/cooler, wetter/drier TREND across
 * the last three issuances.
 *
 * The embedded maps on the Extended Outlook page are just NOAA's pictures;
 * this reads the machine-readable version — CPC's GIS shapefiles (public
 * domain, SHP polygons + DBF attributes) — samples each state, stores one row
 * per issuance, and diffs the three most recent to label the direction.
 *
 * Because consecutive outlooks cover a sliding window, the trend answers "how
 * the lean for the period ahead is evolving," not three looks at fixed days —
 * the UI labels it "last 3 outlooks."
 */
@Service
public class OutlookTrendService {

	private static final String BASE = "https://ftp.cpc.ncep.noaa.gov/GIS/us_tempprcpfcst/";
	private static final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("yyyyMMdd");
	private static final String UA =
		"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36";

	private static final String[] RANGES = { "610", "814" };
	private static final String[] ELEMENTS = { "temp", "precip" };

	/** State abbreviation → representative centroid {lat, lon}. */
	private static final Map<String, double[]> STATES = new LinkedHashMap<>();
	static {
		STATES.put("MN", new double[]{46.3, -94.3}); STATES.put("WI", new double[]{44.6, -89.7});
		STATES.put("MI", new double[]{44.4, -85.4}); STATES.put("IA", new double[]{42.0, -93.5});
		STATES.put("IL", new double[]{40.0, -89.2}); STATES.put("IN", new double[]{39.9, -86.3});
		STATES.put("OH", new double[]{40.3, -82.8}); STATES.put("MO", new double[]{38.4, -92.5});
		STATES.put("KS", new double[]{38.5, -98.3}); STATES.put("NE", new double[]{41.5, -99.8});
		STATES.put("SD", new double[]{44.4, -100.2}); STATES.put("ND", new double[]{47.4, -100.5});
		STATES.put("KY", new double[]{37.5, -85.3}); STATES.put("TN", new double[]{35.9, -86.4});
	}

	private final RestTemplate restTemplate;
	private final OutlookSnapshotRepository repo;

	public OutlookTrendService(RestTemplate restTemplate, OutlookSnapshotRepository repo) {
		this.restTemplate = restTemplate;
		this.repo = repo;
	}

	/* ── scheduling ─────────────────────────────────────────────────────── */

	@EventListener(ApplicationReadyEvent.class)
	public void prewarm() {
		Thread t = new Thread(() -> {
			try { ingestLatest(); } catch (Exception e) {
				System.err.println("[OUTLOOK] startup ingest failed: " + e.getMessage());
			}
		}, "outlook-prewarm");
		t.setDaemon(true);
		t.start();
	}

	/** CPC issues daily; sample every afternoon after the ~3pm ET release. */
	@Scheduled(cron = "0 30 15 * * *", zone = "America/New_York")
	public void scheduledRefresh() {
		try { ingestLatest(); } catch (Exception e) {
			System.err.println("[OUTLOOK] scheduled ingest failed: " + e.getMessage());
		}
	}

	/* ── ingest ─────────────────────────────────────────────────────────── */

	public synchronized void ingestLatest() {
		for (String range : RANGES) {
			for (String elem : ELEMENTS) {
				try { ingestOne(range, elem); } catch (Exception e) {
					System.err.println("[OUTLOOK] " + range + "/" + elem + " failed: "
						+ e.getClass().getSimpleName() + " - " + e.getMessage());
				}
			}
		}
	}

	@Transactional
	void ingestOne(String range, String element) {
		// "temp"/"precip" → CPC file element "temp"/"prcp"; our stored element "TEMP"/"PRECIP".
		String fileElem = element.equals("precip") ? "prcp" : "temp";
		String storeElem = element.equals("precip") ? "PRECIP" : "TEMP";
		String url = BASE + range + fileElem + "_latest.zip";

		byte[] zip = fetchBytes(url);
		if (zip == null || zip.length < 1024) { System.err.println("[OUTLOOK] no zip at " + url); return; }

		Map<String, byte[]> files = unzip(zip);
		byte[] shp = files.get(".shp"), dbf = files.get(".dbf");
		if (shp == null || dbf == null) { System.err.println("[OUTLOOK] zip missing shp/dbf: " + url); return; }

		List<Map<String, String>> attrs = readDbf(dbf);
		List<List<double[][]>> shapes = readShp(shp);
		if (shapes.isEmpty() || attrs.isEmpty()) { System.err.println("[OUTLOOK] empty shapefile: " + url); return; }

		int n = Math.min(shapes.size(), attrs.size());
		List<Poly> polys = new ArrayList<>(n);
		LocalDate issued = null, vStart = null, vEnd = null;
		for (int i = 0; i < n; i++) {
			Map<String, String> a = attrs.get(i);
			if (issued == null) {
				issued = parseDate(a.get("Fcst_Date"));
				vStart = parseDate(a.get("Start_Date"));
				vEnd = parseDate(a.get("End_Date"));
			}
			polys.add(new Poly(shapes.get(i), a.getOrDefault("Cat", "").trim(), parseNum(a.get("Prob"))));
		}
		if (issued == null) { System.err.println("[OUTLOOK] no issuance date in " + url); return; }
		if (repo.existsByIssuedDateAndRangeKeyAndElement(issued, range, storeElem)) return;  // already have it

		LocalDateTime now = LocalDateTime.now();
		double midSum = 0; int midCnt = 0;
		List<OutlookSnapshot> batch = new ArrayList<>();
		for (Map.Entry<String, double[]> e : STATES.entrySet()) {
			double lat = e.getValue()[0], lon = e.getValue()[1];
			Poly best = null;
			for (Poly p : polys)
				if (p.prob() > 0 && contains(p.rings(), lon, lat) && (best == null || p.prob() > best.prob())) best = p;

			OutlookSnapshot s = row(issued, range, storeElem, e.getKey(), best, vStart, vEnd, now);
			batch.add(s);
			double signed = signedProb(s.getCategory(), s.getProb());
			midSum += signed; midCnt++;
		}
		// Midwest composite = mean signed lean across the states.
		double midProb = midCnt == 0 ? 0 : midSum / midCnt;
		OutlookSnapshot mid = new OutlookSnapshot();
		mid.setIssuedDate(issued); mid.setRangeKey(range); mid.setElement(storeElem);
		mid.setLocation("MIDWEST");
		mid.setCategory(midProb > 1 ? "ABOVE" : midProb < -1 ? "BELOW" : "NORMAL");
		mid.setProb(Math.round(Math.abs(midProb) * 10) / 10.0);
		mid.setValidStart(vStart); mid.setValidEnd(vEnd); mid.setFetchedAt(now);
		batch.add(mid);

		// Upsert (idempotent per issuance/range/element/location).
		for (OutlookSnapshot s : batch) {
			OutlookSnapshot existing = repo.findByIssuedDateAndRangeKeyAndElementAndLocation(
				s.getIssuedDate(), s.getRangeKey(), s.getElement(), s.getLocation()).orElse(null);
			if (existing != null) { s.setId(existing.getId()); }
			repo.save(s);
		}
		System.out.println("[OUTLOOK] " + range + "/" + storeElem + " issued " + issued
			+ " sampled " + STATES.size() + " states");
	}

	private OutlookSnapshot row(LocalDate issued, String range, String elem, String loc,
			Poly best, LocalDate vs, LocalDate ve, LocalDateTime now) {
		OutlookSnapshot s = new OutlookSnapshot();
		s.setIssuedDate(issued); s.setRangeKey(range); s.setElement(elem); s.setLocation(loc);
		if (best == null) { s.setCategory("EC"); s.setProb(null); }
		else { s.setCategory(normCat(best.cat())); s.setProb(best.prob()); }
		s.setValidStart(vs); s.setValidEnd(ve); s.setFetchedAt(now);
		return s;
	}

	/** Above → +prob, Below → -prob, Normal/EC → 0. Used for the composite and trend. */
	private static double signedProb(String cat, Double prob) {
		if (prob == null) return 0;
		return switch (cat) {
			case "ABOVE" -> prob;
			case "BELOW" -> -prob;
			default -> 0;
		};
	}

	/* ── read for the API ───────────────────────────────────────────────── */

	public Map<String, Object> getTrends() {
		Map<String, Object> out = new LinkedHashMap<>();
		if (repo.count() == 0) {
			out.put("ranges", Map.of());
			out.put("message", "Outlook trend data isn't loaded yet — it builds up over several days.");
			return out;
		}
		Map<String, Object> ranges = new LinkedHashMap<>();
		for (String range : RANGES) {
			Map<String, Object> byElem = new LinkedHashMap<>();
			for (String elem : new String[]{"TEMP", "PRECIP"}) {
				Map<String, Object> byLoc = new LinkedHashMap<>();
				List<String> locs = new ArrayList<>(STATES.keySet());
				locs.add("MIDWEST");
				for (String loc : locs) {
					Map<String, Object> t = trendFor(range, elem, loc);
					if (t != null) byLoc.put(loc, t);
				}
				byElem.put(elem, byLoc);
			}
			ranges.put(range, byElem);
		}
		out.put("ranges", ranges);
		return out;
	}

	/** {latest, series[], direction} for one range/element/location, or null if <2 issuances. */
	private Map<String, Object> trendFor(String range, String elem, String loc) {
		List<OutlookSnapshot> rows =
			repo.findTop3ByRangeKeyAndElementAndLocationOrderByIssuedDateDesc(range, elem, loc);
		if (rows.isEmpty()) return null;

		// rows are newest-first; series oldest-first for reading left→right.
		List<Map<String, Object>> series = new ArrayList<>();
		for (int i = rows.size() - 1; i >= 0; i--) {
			OutlookSnapshot s = rows.get(i);
			Map<String, Object> m = new LinkedHashMap<>();
			m.put("issued", s.getIssuedDate().toString());
			m.put("category", s.getCategory());
			m.put("prob", s.getProb());
			m.put("signed", Math.round(signedProb(s.getCategory(), s.getProb()) * 10) / 10.0);
			series.add(m);
		}

		OutlookSnapshot latest = rows.get(0);
		Map<String, Object> t = new LinkedHashMap<>();
		t.put("latest", Map.of(
			"category", latest.getCategory(),
			"prob", latest.getProb() == null ? 0.0 : latest.getProb(),
			"issued", latest.getIssuedDate().toString()));
		t.put("validStart", latest.getValidStart() == null ? null : latest.getValidStart().toString());
		t.put("validEnd", latest.getValidEnd() == null ? null : latest.getValidEnd().toString());
		t.put("series", series);
		t.put("count", rows.size());
		t.put("direction", direction(elem, series));
		return t;
	}

	/**
	 * Direction from the signed-lean series (Above positive, Below negative):
	 * a rising temperature lean = "warmer"; rising precip lean = "wetter", etc.
	 * Needs ≥2 points and a change past a small threshold, else "steady".
	 */
	private static String direction(String elem, List<Map<String, Object>> series) {
		if (series.size() < 2) return "new";
		double first = num(series.get(0).get("signed"));
		double last = num(series.get(series.size() - 1).get("signed"));
		double delta = last - first;
		boolean temp = "TEMP".equals(elem);
		if (Math.abs(delta) < 5) return "steady";
		if (delta > 0) return temp ? "warmer" : "wetter";
		return temp ? "cooler" : "drier";
	}

	private static double num(Object o) { return o == null ? 0 : ((Number) o).doubleValue(); }

	/* ── shapefile parsing (validated against real CPC files) ──────────── */

	private record Poly(List<double[][]> rings, String cat, double prob) {}

	private static boolean contains(List<double[][]> rings, double lon, double lat) {
		boolean in = false;
		for (double[][] ring : rings) {
			for (int i = 0, j = ring.length - 1; i < ring.length; j = i++) {
				double yi = ring[i][1], yj = ring[j][1];
				if ((yi > lat) != (yj > lat)) {
					double xi = ring[i][0], xj = ring[j][0];
					if (lon < (xj - xi) * (lat - yi) / (yj - yi) + xi) in = !in;
				}
			}
		}
		return in;
	}

	/** SHP type-5 polygons → rings per record. */
	private static List<List<double[][]>> readShp(byte[] b) {
		List<List<double[][]>> out = new ArrayList<>();
		ByteBuffer be = ByteBuffer.wrap(b).order(ByteOrder.BIG_ENDIAN);
		ByteBuffer le = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN);
		int pos = 100;
		while (pos + 8 <= b.length) {
			int contentLen = be.getInt(pos + 4) * 2;
			int rec = pos + 8;
			if (rec + 4 > b.length) break;
			int shapeType = le.getInt(rec);
			List<double[][]> rings = new ArrayList<>();
			if (shapeType == 5) {
				int numParts = le.getInt(rec + 36);
				int numPoints = le.getInt(rec + 40);
				int partsOff = rec + 44;
				int pointsOff = partsOff + 4 * numParts;
				int[] parts = new int[numParts + 1];
				for (int i = 0; i < numParts; i++) parts[i] = le.getInt(partsOff + 4 * i);
				parts[numParts] = numPoints;
				for (int i = 0; i < numParts; i++) {
					int len = parts[i + 1] - parts[i];
					double[][] ring = new double[len][2];
					for (int k = 0; k < len; k++) {
						ring[k][0] = le.getDouble(pointsOff + 16 * (parts[i] + k));
						ring[k][1] = le.getDouble(pointsOff + 16 * (parts[i] + k) + 8);
					}
					rings.add(ring);
				}
			}
			out.add(rings);
			pos = rec + contentLen;
		}
		return out;
	}

	/** DBF fixed-width ASCII records → field maps. */
	private static List<Map<String, String>> readDbf(byte[] b) {
		ByteBuffer le = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN);
		int numRec = le.getInt(4);
		int headerSize = Short.toUnsignedInt(le.getShort(8));
		int recSize = Short.toUnsignedInt(le.getShort(10));
		List<String> names = new ArrayList<>();
		List<Integer> lens = new ArrayList<>();
		for (int off = 32; off < headerSize - 1 && b[off] != 0x0D; off += 32) {
			int end = off;
			while (end < off + 11 && b[end] != 0) end++;
			names.add(new String(b, off, end - off, StandardCharsets.US_ASCII));
			lens.add(Byte.toUnsignedInt(b[off + 16]));
		}
		List<Map<String, String>> out = new ArrayList<>();
		for (int r = 0; r < numRec; r++) {
			int base = headerSize + r * recSize + 1;                 // +1 deletion flag
			if (base + recSize - 1 > b.length) break;
			Map<String, String> row = new LinkedHashMap<>();
			int off = base;
			for (int f = 0; f < names.size(); f++) {
				row.put(names.get(f),
					new String(b, off, lens.get(f), StandardCharsets.US_ASCII).trim());
				off += lens.get(f);
			}
			out.add(row);
		}
		return out;
	}

	/* ── helpers ────────────────────────────────────────────────────────── */

	private static String normCat(String cat) {
		String c = cat == null ? "" : cat.trim().toLowerCase();
		if (c.startsWith("a")) return "ABOVE";
		if (c.startsWith("b")) return "BELOW";
		if (c.startsWith("n")) return "NORMAL";
		return "EC";
	}

	private static LocalDate parseDate(String s) {
		if (s == null || s.isBlank()) return null;
		try { return LocalDate.parse(s.trim(), YMD); } catch (Exception e) { return null; }
	}
	private static double parseNum(String s) {
		try { return Double.parseDouble(s.trim()); } catch (Exception e) { return 0; }
	}

	private Map<String, byte[]> unzip(byte[] zip) {
		Map<String, byte[]> out = new LinkedHashMap<>();
		try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zip))) {
			ZipEntry e;
			byte[] buf = new byte[8192];
			while ((e = zis.getNextEntry()) != null) {
				String name = e.getName().toLowerCase();
				int dot = name.lastIndexOf('.');
				if (dot < 0) continue;
				String ext = name.substring(dot);
				if (!ext.equals(".shp") && !ext.equals(".dbf")) continue;
				var bos = new java.io.ByteArrayOutputStream();
				int r;
				while ((r = zis.read(buf)) > 0) bos.write(buf, 0, r);
				out.put(ext, bos.toByteArray());
			}
		} catch (Exception ex) {
			System.err.println("[OUTLOOK] unzip failed: " + ex.getMessage());
		}
		return out;
	}

	private byte[] fetchBytes(String url) {
		try {
			HttpHeaders h = new HttpHeaders();
			h.set("User-Agent", UA);
			ResponseEntity<byte[]> resp =
				restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(h), byte[].class);
			return resp.getStatusCode().is2xxSuccessful() ? resp.getBody() : null;
		} catch (Exception e) {
			System.err.println("[OUTLOOK] fetch " + url + " failed: "
				+ e.getClass().getSimpleName() + " - " + e.getMessage());
			return null;
		}
	}
}
