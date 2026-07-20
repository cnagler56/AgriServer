package com.home.Service;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.home.Domain.VegetationSnapshot;
import com.home.Repository.VegetationSnapshotRepository;

/**
 * County-level Vegetation Health Index for the Midwest, computed from NOAA
 * STAR's weekly 4km VHP composite (public domain).
 *
 * Pipeline: scrape the NOAA directory listing for the newest weekly VHI
 * GeoTIFF → download it → decode ONLY the rows covering our counties (the
 * file is strip-per-row LZW, so a windowed read is cheap) → average the
 * valid cells inside each county polygon (ray-casting point-in-polygon
 * against the same county geojson the frontend renders) → upsert one row
 * per county per week.
 *
 * Follows the site's durable-cache pattern: history lives in the DB, the
 * API serves the newest stored week, and any failure just leaves the last
 * good week in place.
 */
@Service
public class VegetationService {

	private static final String DIR_URL =
		"https://www.star.nesdis.noaa.gov/data/pub0018/VHPdata4users/data/Blended_VH_4km/geo_TIFF/";
	/** e.g. VHP.G04.C07.j01.P2026028.VH.VHI.tif — satellite tag varies, so capture it. */
	private static final Pattern FILE_RX =
		Pattern.compile("VHP\\.G04\\.C07\\.([A-Za-z0-9]+)\\.P(\\d{4})(\\d{3})\\.VH\\.VHI\\.tif");
	private static final String UA =
		"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36";

	private final RestTemplate restTemplate;
	private final VegetationSnapshotRepository repo;

	/** County polygons parsed from the bundled geojson (lazy, immutable after load). */
	private volatile List<County> counties;

	public VegetationService(RestTemplate restTemplate, VegetationSnapshotRepository repo) {
		this.restTemplate = restTemplate;
		this.repo = repo;
	}

	/* ── scheduling ─────────────────────────────────────────────────────── */

	@EventListener(ApplicationReadyEvent.class)
	public void prewarm() {
		Thread t = new Thread(() -> {
			try { refreshIfStale(); } catch (Exception e) {
				System.err.println("[VEG] startup ingest failed: " + e.getMessage());
			}
		}, "veg-prewarm");
		t.setDaemon(true);
		t.start();
	}

	/** NOAA posts the new week early in the week; check a few mornings. */
	@Scheduled(cron = "0 40 5 * * TUE,WED,SAT", zone = "America/Chicago")
	public void scheduledRefresh() {
		try { refreshIfStale(); } catch (Exception e) {
			System.err.println("[VEG] scheduled ingest failed: " + e.getMessage());
		}
	}

	public synchronized void refreshIfStale() {
		Available avail = findLatestAvailable();
		if (avail == null) {
			System.err.println("[VEG] could not find a VHI file in the NOAA listing");
			return;
		}
		Integer stored = repo.latestKey();
		int availKey = avail.year() * 100 + avail.week();
		if (stored != null && stored >= availKey) return;   // up to date
		ingest(avail);
	}

	/* ── ingest ─────────────────────────────────────────────────────────── */

	private record Available(String filename, int year, int week) {}

	/** Newest weekly VHI file per the NOAA directory listing. */
	private Available findLatestAvailable() {
		String html = fetchText(DIR_URL);
		if (html == null) return null;
		Available best = null;
		Matcher m = FILE_RX.matcher(html);
		while (m.find()) {
			int year = Integer.parseInt(m.group(2));
			int week = Integer.parseInt(m.group(3));
			if (best == null || year * 100 + week > best.year() * 100 + best.week()) {
				best = new Available(m.group(), year, week);
			}
		}
		return best;
	}

	@Transactional
	void ingest(Available avail) {
		System.out.println("[VEG] ingesting " + avail.filename());
		byte[] tif = fetchBytes(DIR_URL + avail.filename());
		if (tif == null || tif.length < 1024) {
			System.err.println("[VEG] download failed for " + avail.filename());
			return;
		}
		List<County> cs = counties();
		if (cs.isEmpty()) {
			System.err.println("[VEG] no county polygons loaded");
			return;
		}

		Grid grid;
		try {
			grid = Grid.decodeWindow(tif, bounds(cs));
		} catch (Exception e) {
			System.err.println("[VEG] TIFF decode failed (format change?): "
				+ e.getClass().getSimpleName() + " - " + e.getMessage());
			return;
		}

		LocalDateTime now = LocalDateTime.now();
		int saved = 0;
		for (County c : cs) {
			double sum = 0;
			int cnt = 0;
			int r0 = grid.rowOf(c.maxLat()), r1 = grid.rowOf(c.minLat());
			int c0 = grid.colOf(c.minLon()), c1 = grid.colOf(c.maxLon());
			for (int r = Math.max(r0, 0); r <= r1 && r < grid.rows(); r++) {
				double lat = grid.latOf(r);
				for (int col = Math.max(c0, 0); col <= c1 && col < grid.cols(); col++) {
					float v = grid.value(r, col);
					if (v < 0 || v > 100) continue;                 // -9999 fill, water, junk
					if (c.contains(grid.lonOf(col), lat)) { sum += v; cnt++; }
				}
			}
			if (cnt == 0) continue;                                 // county smaller than the grid — skip
			VegetationSnapshot s = repo
				.findByFipsAndYearAndWeek(c.fips(), avail.year(), avail.week())
				.orElseGet(VegetationSnapshot::new);
			s.setFips(c.fips());
			s.setCountyName(c.name());
			s.setYear(avail.year());
			s.setWeek(avail.week());
			s.setVhi(Math.round((sum / cnt) * 10) / 10.0);
			s.setCells(cnt);
			s.setFetchedAt(now);
			repo.save(s);
			saved++;
		}
		System.out.println("[VEG] stored VHI for " + saved + " counties, week "
			+ avail.year() + "-" + avail.week());
	}

	/* ── read for the API ───────────────────────────────────────────────── */

	public Map<String, Object> getCounties() {
		Map<String, Object> out = new LinkedHashMap<>();
		Integer key = repo.latestKey();
		if (key == null) {
			out.put("byFips", Map.of());
			out.put("message", "Vegetation data isn't loaded yet — check back shortly.");
			return out;
		}
		int year = key / 100, week = key % 100;
		List<VegetationSnapshot> rows = repo.findByYearAndWeek(year, week);

		Map<String, Double> byFips = new LinkedHashMap<>();
		LocalDateTime updated = null;
		for (VegetationSnapshot r : rows) {
			byFips.put(r.getFips(), r.getVhi());
			if (r.getFetchedAt() != null && (updated == null || r.getFetchedAt().isAfter(updated))) {
				updated = r.getFetchedAt();
			}
		}
		out.put("year", year);
		out.put("week", week);
		out.put("weekEnding", weekEnding(year, week).toString());
		out.put("counties", rows.size());
		out.put("updatedAt", updated == null ? null : updated.toString());
		out.put("byFips", byFips);
		return out;
	}

	/** VHP week n covers year-days (n-1)*7+1 .. n*7; return the period's end date. */
	private static LocalDate weekEnding(int year, int week) {
		int maxDay = LocalDate.ofYearDay(year, 1).isLeapYear() ? 366 : 365;
		return LocalDate.ofYearDay(year, Math.min(week * 7, maxDay));
	}

	/* ── county polygons ────────────────────────────────────────────────── */

	/** fips + name + bbox + rings (each ring = [ [lon,lat], ... ]). */
	record County(String fips, String name, double minLat, double maxLat,
			double minLon, double maxLon, List<double[][]> rings) {

		/** Even-odd ray casting across every ring (outer + holes alike). */
		boolean contains(double lon, double lat) {
			if (lat < minLat || lat > maxLat || lon < minLon || lon > maxLon) return false;
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
	}

	private List<County> counties() {
		List<County> cs = counties;
		if (cs != null) return cs;
		synchronized (this) {
			if (counties != null) return counties;
			counties = loadCounties();
			return counties;
		}
	}

	private List<County> loadCounties() {
		List<County> out = new ArrayList<>();
		try (var in = getClass().getResourceAsStream("/data/midwest-counties.geojson")) {
			JsonNode root = new ObjectMapper().readTree(in);
			for (JsonNode f : root.path("features")) {
				String fips = f.path("id").asText(null);
				if (fips == null || fips.isBlank()) continue;
				String name = f.path("properties").path("name").asText("");
				JsonNode geom = f.path("geometry");
				List<double[][]> rings = new ArrayList<>();
				String type = geom.path("type").asText("");
				if ("Polygon".equals(type)) {
					for (JsonNode ring : geom.path("coordinates")) rings.add(toRing(ring));
				} else if ("MultiPolygon".equals(type)) {
					for (JsonNode poly : geom.path("coordinates"))
						for (JsonNode ring : poly) rings.add(toRing(ring));
				} else {
					continue;
				}
				double minLat = 90, maxLat = -90, minLon = 180, maxLon = -180;
				for (double[][] ring : rings) {
					for (double[] p : ring) {
						minLon = Math.min(minLon, p[0]); maxLon = Math.max(maxLon, p[0]);
						minLat = Math.min(minLat, p[1]); maxLat = Math.max(maxLat, p[1]);
					}
				}
				out.add(new County(fips, name, minLat, maxLat, minLon, maxLon, rings));
			}
		} catch (Exception e) {
			System.err.println("[VEG] county geojson load failed: " + e.getMessage());
		}
		System.out.println("[VEG] loaded " + out.size() + " county polygons");
		return out;
	}

	private static double[][] toRing(JsonNode ring) {
		double[][] pts = new double[ring.size()][2];
		for (int i = 0; i < ring.size(); i++) {
			pts[i][0] = ring.get(i).get(0).asDouble();
			pts[i][1] = ring.get(i).get(1).asDouble();
		}
		return pts;
	}

	private static double[] bounds(List<County> cs) {
		double minLat = 90, maxLat = -90, minLon = 180, maxLon = -180;
		for (County c : cs) {
			minLat = Math.min(minLat, c.minLat()); maxLat = Math.max(maxLat, c.maxLat());
			minLon = Math.min(minLon, c.minLon()); maxLon = Math.max(maxLon, c.maxLon());
		}
		return new double[] { minLat, maxLat, minLon, maxLon };
	}

	/* ── HTTP ───────────────────────────────────────────────────────────── */

	private String fetchText(String url) {
		byte[] b = fetchBytes(url);
		return b == null ? null : new String(b, StandardCharsets.ISO_8859_1);
	}

	private byte[] fetchBytes(String url) {
		try {
			HttpHeaders h = new HttpHeaders();
			h.set("User-Agent", UA);
			ResponseEntity<byte[]> resp =
				restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(h), byte[].class);
			return resp.getStatusCode().is2xxSuccessful() ? resp.getBody() : null;
		} catch (Exception e) {
			System.err.println("[VEG] fetch " + url + " failed: "
				+ e.getClass().getSimpleName() + " - " + e.getMessage());
			return null;
		}
	}

	/* ── GeoTIFF windowed reader (validated against real VHP files) ─────── */

	/**
	 * A decoded window of the VHP grid: float32 rows between latMin/latMax.
	 * Georeferencing is read from the file's GeoTIFF tags rather than assumed,
	 * so a grid change on NOAA's side fails loudly instead of shifting counties.
	 */
	static final class Grid {
		private final float[][] data;      // [rowInWindow][col]
		private final int rowStart;        // first decoded absolute row
		private final int width;
		private final double lon0, lat0, step;

		private Grid(float[][] data, int rowStart, int width, double lon0, double lat0, double step) {
			this.data = data; this.rowStart = rowStart; this.width = width;
			this.lon0 = lon0; this.lat0 = lat0; this.step = step;
		}

		int rows() { return rowStart + data.length; }
		int cols() { return width; }
		int rowOf(double lat) { return (int) Math.floor((lat0 - lat) / step); }
		int colOf(double lon) { return (int) Math.floor((lon - lon0) / step); }
		double latOf(int row) { return lat0 - (row + 0.5) * step; }
		double lonOf(int col) { return lon0 + (col + 0.5) * step; }

		float value(int row, int col) {
			int r = row - rowStart;
			if (r < 0 || r >= data.length || col < 0 || col >= width) return -9999f;
			return data[r][col];
		}

		/** Decode only the strips covering bounds = {minLat, maxLat, minLon, maxLon}. */
		static Grid decodeWindow(byte[] tif, double[] b) {
			ByteBuffer bb = ByteBuffer.wrap(tif);
			if (tif[0] == 'I' && tif[1] == 'I') bb.order(ByteOrder.LITTLE_ENDIAN);
			else if (tif[0] == 'M' && tif[1] == 'M') bb.order(ByteOrder.BIG_ENDIAN);
			else throw new IllegalStateException("not a TIFF");

			long ifd = Integer.toUnsignedLong(bb.getInt(4));
			int n = Short.toUnsignedInt(bb.getShort((int) ifd));
			int width = 0, height = 0, compression = 0, bits = 0, sampleFormat = 0, rowsPerStrip = 0;
			long stripOffPtr = 0, stripCntPtr = 0, scalePtr = 0, tiePtr = 0;
			for (int i = 0; i < n; i++) {
				int e = (int) ifd + 2 + i * 12;
				int tag = Short.toUnsignedInt(bb.getShort(e));
				long val = Integer.toUnsignedLong(bb.getInt(e + 8));
				switch (tag) {
					case 256 -> width = (int) val;
					case 257 -> height = (int) val;
					case 258 -> bits = (int) val;
					case 259 -> compression = (int) val;
					case 273 -> stripOffPtr = val;
					case 278 -> rowsPerStrip = (int) val;
					case 279 -> stripCntPtr = val;
					case 339 -> sampleFormat = (int) val;
					case 33550 -> scalePtr = val;
					case 33922 -> tiePtr = val;
				}
			}
			if (width <= 0 || height <= 0) throw new IllegalStateException("bad dimensions");
			if (compression != 5) throw new IllegalStateException("expected LZW, got " + compression);
			if (bits != 32 || sampleFormat != 3) throw new IllegalStateException("expected float32 samples");
			if (rowsPerStrip != 1) throw new IllegalStateException("expected strip-per-row layout");
			if (scalePtr == 0 || tiePtr == 0) throw new IllegalStateException("missing georeferencing");

			double step = bb.getDouble((int) scalePtr);          // x scale; y assumed equal
			double lon0 = bb.getDouble((int) tiePtr + 24);       // tiepoint: i,j,k,lon,lat,z
			double lat0 = bb.getDouble((int) tiePtr + 32);

			int r0 = Math.max(0, (int) Math.floor((lat0 - b[1]) / step) - 1);
			int r1 = Math.min(height - 1, (int) Math.ceil((lat0 - b[0]) / step) + 1);

			float[][] data = new float[r1 - r0 + 1][];
			for (int r = r0; r <= r1; r++) {
				long off = Integer.toUnsignedLong(bb.getInt((int) (stripOffPtr + 4L * r)));
				int len = bb.getInt((int) (stripCntPtr + 4L * r));
				byte[] rowBytes = lzwDecode(tif, (int) off, len, width * 4);
				float[] row = new float[width];
				ByteBuffer rb = ByteBuffer.wrap(rowBytes).order(bb.order());
				for (int c = 0; c < width; c++) row[c] = rb.getFloat(c * 4);
				data[r - r0] = row;
			}
			return new Grid(data, r0, width, lon0, lat0, step);
		}

		/** TIFF-variant LZW (MSB-first codes, 9→12 bits with early change). */
		static byte[] lzwDecode(byte[] src, int off, int len, int outCap) {
			byte[] out = new byte[outCap];
			int outPos = 0;
			byte[][] table = new byte[4096][];
			for (int i = 0; i < 256; i++) table[i] = new byte[] { (byte) i };
			int next = 258, bitsLen = 9;
			long acc = 0;
			int nbits = 0, pos = off, end = off + len;
			byte[] prev = null;
			while (pos < end || nbits >= bitsLen) {
				while (nbits < bitsLen && pos < end) { acc = (acc << 8) | (src[pos++] & 0xff); nbits += 8; }
				if (nbits < bitsLen) break;
				int code = (int) ((acc >> (nbits - bitsLen)) & ((1 << bitsLen) - 1));
				nbits -= bitsLen;
				if (code == 256) { next = 258; bitsLen = 9; prev = null; continue; }
				if (code == 257) break;
				byte[] entry;
				if (code < next && table[code] != null) {
					entry = table[code];
				} else if (prev != null) {
					entry = new byte[prev.length + 1];
					System.arraycopy(prev, 0, entry, 0, prev.length);
					entry[prev.length] = prev[0];
				} else break;
				int copy = Math.min(entry.length, outCap - outPos);
				System.arraycopy(entry, 0, out, outPos, copy);
				outPos += copy;
				if (outPos >= outCap) break;
				if (prev != null && next < 4096) {
					byte[] ne = new byte[prev.length + 1];
					System.arraycopy(prev, 0, ne, 0, prev.length);
					ne[prev.length] = entry[0];
					table[next++] = ne;
				}
				if (next == (1 << bitsLen) - 1 && bitsLen < 12) bitsLen++;
				prev = entry;
			}
			return out;
		}
	}
}
