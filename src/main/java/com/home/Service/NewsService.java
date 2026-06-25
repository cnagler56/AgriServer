package com.home.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.home.Domain.CommodityGroup;
import com.home.Domain.CommodityPrice;
import com.home.Domain.NewsItem;
import com.home.Repository.NewsItemRepository;

/**
 * Builds the home-page "Latest News" feed from the data we already ingest —
 * WASDE, weekly ethanol, ENSO, and notable daily price moves. Each distinct
 * release is saved once (deduped) with a first-seen timestamp; the feed shows
 * items from the last {@link #SHELF_DAYS} days, so each item rolls off ~3 days
 * after it appears regardless of how its source releases.
 */
@Service
public class NewsService {

	private static final int SHELF_DAYS = 3;
	private static final int KEEP_DAYS = 14;          // housekeeping retention
	private static final double PRICE_MOVE_PCT = 2.0; // only flag moves this big

	private final NewsItemRepository repo;
	private final EthanolService ethanolService;
	private final EnsoService ensoService;
	private final SupplyDemandService supplyDemandService;
	private final PriceService priceService;
	private final CotService cotService;
	private final BroilerService broilerService;
	private final ExportSalesService exportSalesService;
	private final GrainStocksService grainStocksService;

	public NewsService(NewsItemRepository repo, EthanolService ethanolService, EnsoService ensoService,
			SupplyDemandService supplyDemandService, PriceService priceService, CotService cotService,
			BroilerService broilerService, ExportSalesService exportSalesService,
			GrainStocksService grainStocksService) {
		this.repo = repo;
		this.ethanolService = ethanolService;
		this.ensoService = ensoService;
		this.supplyDemandService = supplyDemandService;
		this.priceService = priceService;
		this.cotService = cotService;
		this.broilerService = broilerService;
		this.exportSalesService = exportSalesService;
		this.grainStocksService = grainStocksService;
	}

	/** Page name per priced commodity, for the item link. */
	private static final Map<String, String> PRICE_LINKS = Map.of(
		"Corn", "/corn", "Soybeans", "/soybeans", "Wheat", "/wheat",
		"Soybean Meal", "/soybean-meal", "Soybean Oil", "/soybean-oil",
		"Live Cattle", "/cattle", "Lean Hogs", "/hogs");

	/* ── scheduling ─────────────────────────────────────────────────────── */

	@EventListener(ApplicationReadyEvent.class)
	public void prewarm() {
		new Thread(() -> {
			try {
				Thread.sleep(15_000); // let the data services finish their own prewarm first
				generate();
			} catch (Exception e) {
				System.err.println("[NEWS] startup generate failed: " + e.getMessage());
			}
		}, "news-prewarm").start();
	}

	/** Re-scan the feeds a few times a day; new releases become news within hours. */
	@Scheduled(cron = "0 5 */2 * * *", zone = "America/Chicago")
	public void scheduled() {
		try {
			generate();
			repo.deleteByCreatedAtBefore(LocalDateTime.now().minusDays(KEEP_DAYS));
		} catch (Exception e) {
			System.err.println("[NEWS] scheduled generate failed: " + e.getMessage());
		}
	}

	/* ── generation ─────────────────────────────────────────────────────── */

	public void generate() {
		ethanolItem();
		gasolineItem();
		ensoItem();
		wasdeItem();
		priceItem();
		cotItem();
		broilerItem();
		exportSalesItem();
		grainStocksItem();
	}

	private void save(String key, String category, String icon, String headline, String detail,
			String link, String eventDate) {
		if (key == null || headline == null) return;
		if (repo.existsByDedupeKey(key)) return;
		NewsItem n = new NewsItem();
		n.setDedupeKey(key);
		n.setCategory(category);
		n.setIcon(icon);
		n.setHeadline(headline);
		n.setDetail(detail);
		n.setLink(link);
		n.setEventDate(eventDate);
		try {
			repo.save(n);
			System.out.println("[NEWS] + " + headline);
		} catch (Exception e) {
			// Unique-key race or constraint hit — fine, it already exists.
		}
	}

	private void ethanolItem() {
		try {
			Map<String, Object> e = ethanolService.getEthanol();
			Double latest = dbl(e.get("productionLatest"));
			String asOf = str(e.get("productionAsOf"));
			if (latest == null || asOf == null) return;
			Double wow = dbl(e.get("productionWoW"));
			Double cornWeek = dbl(e.get("impliedCornBuPerWeek"));
			String move = wow == null || wow == 0 ? ""
				: wow > 0 ? ", up " + fmt(Math.abs(wow)) + "k w/w"
				: ", down " + fmt(Math.abs(wow)) + "k w/w";
			String detail = cornWeek == null ? null
				: "Implied corn grind ~" + fmt(cornWeek / 1_000_000.0) + "M bu/week";
			save("ETHANOL|" + asOf, "ETHANOL", "⛽",
				"Ethanol output " + fmt(latest) + "k bbl/day" + move,
				detail, "/ethanol", shortDate(asOf));
		} catch (Exception ex) {
			System.err.println("[NEWS] ethanol item failed: " + ex.getMessage());
		}
	}

	private void gasolineItem() {
		try {
			Map<String, Object> e = ethanolService.getEthanol();
			Double latest = dbl(e.get("gasolineLatest"));
			String asOf = str(e.get("gasolineAsOf"));
			if (latest == null || asOf == null) return;
			Double wow = dbl(e.get("gasolineWoW"));
			Double yoy = dbl(e.get("gasolineYoYPct"));
			String move = wow == null || wow == 0 ? ""
				: ", " + (wow > 0 ? "up " : "down ") + fmt(Math.abs(wow)) + "k b/d w/w";
			String detail = yoy == null ? null : "Year-over-year " + signed(yoy) + "% · ethanol-blend demand";
			save("GASDEM|" + asOf, "ENERGY", "🚗",
				"EIA: U.S. gasoline demand " + fmt(latest / 1000.0) + "M bbl/day" + move,
				detail, "/ethanol", shortDate(asOf));
		} catch (Exception ex) {
			System.err.println("[NEWS] gasoline item failed: " + ex.getMessage());
		}
	}

	private void ensoItem() {
		try {
			Map<String, Object> e = ensoService.getEnso();
			@SuppressWarnings("unchecked")
			Map<String, Object> cur = (Map<String, Object>) e.get("current");
			if (cur == null) return;
			String label = str(cur.get("label"));
			Double oni = dbl(cur.get("oni"));
			String season = str(cur.get("season"));
			Object year = cur.get("year");
			if (label == null || oni == null || season == null || year == null) return;
			save("ENSO|" + season + year, "ENSO", "🌊",
				"ENSO update: " + label + " (ONI " + signed(oni) + "°C)",
				str(cur.get("strength")).isBlank() ? null : cur.get("strength") + " " + label,
				"/enso", season + " " + year);
		} catch (Exception ex) {
			System.err.println("[NEWS] enso item failed: " + ex.getMessage());
		}
	}

	@SuppressWarnings("unchecked")
	private void wasdeItem() {
		try {
			Map<String, Object> sd = supplyDemandService.getBalanceSheet("CORN");
			String reportDate = str(sd.get("reportDate"));
			if (reportDate == null) return;
			List<Map<String, Object>> us = (List<Map<String, Object>>) sd.get("us");
			Double ending = null;
			if (us != null) {
				for (Map<String, Object> row : us) {
					String attr = str(row.get("attribute"));
					if (attr != null && attr.toLowerCase().contains("ending stocks")) {
						List<Object> vals = (List<Object>) row.get("values");
						if (vals != null && !vals.isEmpty()) ending = dbl(vals.get(0));
						break;
					}
				}
			}
			String headline = ending != null
				? reportDate + " WASDE: U.S. corn ending stocks " + fmt(ending) + " mil bu"
				: reportDate + " WASDE corn balance sheet updated";
			save("WASDE|" + reportDate, "WASDE", "🏛️", headline,
				"New-crop U.S. supply & demand", "/corn", reportDate);
		} catch (Exception ex) {
			System.err.println("[NEWS] wasde item failed: " + ex.getMessage());
		}
	}

	private void priceItem() {
		try {
			CommodityPrice best = null;
			String bestName = null;
			for (CommodityGroup g : priceService.getPricesGrouped()) {
				if (g.getContracts() == null || g.getContracts().isEmpty()) continue;
				CommodityPrice front = g.getContracts().get(0);
				if (front.getChangePercent() == null) continue;
				if (best == null || Math.abs(front.getChangePercent()) > Math.abs(best.getChangePercent())) {
					best = front; bestName = g.getName();
				}
			}
			if (best == null || best.getChangePercent() == null
				|| Math.abs(best.getChangePercent()) < PRICE_MOVE_PCT || best.getAsOf() == null) return;

			String day = Instant.ofEpochSecond(best.getAsOf()).atZone(ZoneId.of("America/Chicago")).toLocalDate().toString();
			String chg = (best.getChange() != null ? (best.getChange() > 0 ? "+" : "") + fmt(best.getChange()) : "");
			save("PRICE|" + day + "|" + best.getSymbol(), "PRICE", "📈",
				bestName + " " + best.getExpiration() + " " + chg + " (" + signed(best.getChangePercent()) + "%)",
				"Biggest mover today · " + best.getLast() + " " + best.getUnit(),
				PRICE_LINKS.getOrDefault(bestName, "/home"), shortDate(day));
		} catch (Exception ex) {
			System.err.println("[NEWS] price item failed: " + ex.getMessage());
		}
	}

	private void cotItem() {
		try {
			String date = cotService.getReportDate();
			if (date == null) return;
			CotService.Pos corn = cotService.getPosition("CORN");
			CotService.Pos soy = cotService.getPosition("SOYBEANS");
			CotService.Pos wheat = cotService.getPosition("WHEAT");
			if (corn == null && soy == null) return;
			String headline = "CFTC: managed money"
				+ (corn != null ? " " + stance(corn.net()) + " corn" : "")
				+ (soy != null ? ", " + stance(soy.net()) + " soybeans" : "");
			StringBuilder detail = new StringBuilder();
			if (corn != null) detail.append("Corn ").append(signedK(corn.net()));
			if (soy != null) detail.append(" · Soybeans ").append(signedK(soy.net()));
			if (wheat != null) detail.append(" · SRW wheat ").append(signedK(wheat.net()));
			detail.append(" (net contracts)");
			save("COT|" + date, "COT", "📊", headline, detail.toString(), "/corn", shortDate(date));
		} catch (Exception ex) {
			System.err.println("[NEWS] cot item failed: " + ex.getMessage());
		}
	}

	private void broilerItem() {
		try {
			Map<String, Object> b = broilerService.getLatest();
			String weekEnding = str(b.get("weekEnding"));
			Double head = dbl(b.get("placements"));
			if (weekEnding == null || head == null) return;
			Double wow = dbl(b.get("wowPct"));
			Double yoy = dbl(b.get("yoyPct"));
			String move = wow == null || wow == 0 ? ""
				: ", " + (wow > 0 ? "up " : "down ") + fmt(Math.abs(wow)) + "% w/w";
			String detail = yoy == null ? null : "Year-over-year " + signed(yoy) + "%";
			save("BROILER|" + weekEnding, "POULTRY", "🐔",
				"USDA: broiler chicks placed " + fmt(head / 1_000_000.0) + "M head" + move,
				detail, null, shortDate(weekEnding));
		} catch (Exception ex) {
			System.err.println("[NEWS] broiler item failed: " + ex.getMessage());
		}
	}

	/** Weekly FAS export sales — combined corn / soybeans / wheat net sales. */
	private void exportSalesItem() {
		try {
			Map<String, Object> corn = exportSalesService.getExportSales("CORN");
			String week = str(corn.get("weekEnding"));
			if (week == null) return;
			Double cornNet = dbl(corn.get("netSales"));
			Double soyNet = dbl(exportSalesService.getExportSales("SOYBEANS").get("netSales"));
			Double wheatNet = dbl(exportSalesService.getExportSales("WHEAT").get("netSales"));
			if (cornNet == null && soyNet == null) return;
			String headline = "FAS export sales"
				+ (cornNet != null ? ": corn " + mmt(cornNet) : "")
				+ (soyNet != null ? ", soybeans " + mmt(soyNet) : "")
				+ (wheatNet != null ? ", wheat " + mmt(wheatNet) : "");
			save("ESR|" + week, "EXPORTS", "🚢", headline,
				"Net new sales for the week", "/corn", shortDate(week));
		} catch (Exception ex) {
			System.err.println("[NEWS] export sales item failed: " + ex.getMessage());
		}
	}

	/** Quarterly NASS Grain Stocks — combined corn / soybeans / wheat totals. */
	private void grainStocksItem() {
		try {
			Map<String, Object> corn = grainStocksService.getStocks("CORN");
			String period = str(corn.get("period"));
			Double cornTot = dbl(corn.get("total"));
			if (period == null || cornTot == null) return;
			Double soyTot = dbl(grainStocksService.getStocks("SOYBEANS").get("total"));
			Double wheatTot = dbl(grainStocksService.getStocks("WHEAT").get("total"));
			Double cornYoy = dbl(corn.get("yoyPct"));
			String headline = "NASS Grain Stocks (" + period + "): corn " + bil(cornTot) + " bil bu"
				+ (cornYoy != null ? " (" + signed(cornYoy) + "% YoY)" : "");
			StringBuilder detail = new StringBuilder();
			if (soyTot != null) detail.append("Soybeans ").append(bil(soyTot)).append(" bil bu");
			if (wheatTot != null) detail.append(detail.length() > 0 ? " · " : "").append("Wheat ").append(bil(wheatTot)).append(" bil bu");
			save("STOCKS|" + period, "STOCKS", "📦", headline,
				detail.length() > 0 ? detail.toString() : null, "/corn", period);
		} catch (Exception ex) {
			System.err.println("[NEWS] grain stocks item failed: " + ex.getMessage());
		}
	}

	/** Metric tons → "1.16 MMT". */
	private static String mmt(double mt) { return (Math.round(mt / 1_000.0) / 1_000.0) + " MMT"; }
	/** Bushels → "9.0". */
	private static String bil(double bu) { return String.valueOf(Math.round(bu / 1e9 * 10) / 10.0); }

	private static String stance(long net) {
		return net > 0 ? "net-long" : net < 0 ? "net-short" : "flat";
	}
	private static String signedK(long net) {
		long a = Math.abs(net);
		String mag = a >= 1000 ? (Math.round(a / 100.0) / 10.0) + "k" : String.valueOf(a);
		return (net > 0 ? "+" : net < 0 ? "−" : "") + mag;
	}
	private static String shortDate(String iso) {
		try {
			var d = java.time.LocalDate.parse(iso.substring(0, 10));
			return d.getMonth().getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.US) + " " + d.getDayOfMonth();
		} catch (Exception e) { return iso; }
	}

	/* ── read for the API ───────────────────────────────────────────────── */

	public List<Map<String, Object>> getNews() {
		LocalDateTime cutoff = LocalDateTime.now().minusDays(SHELF_DAYS);
		List<Map<String, Object>> out = new ArrayList<>();
		for (NewsItem n : repo.findByCreatedAtAfterOrderByCreatedAtDesc(cutoff)) {
			Map<String, Object> m = new LinkedHashMap<>();
			m.put("category", n.getCategory());
			m.put("icon", n.getIcon());
			m.put("headline", n.getHeadline());
			m.put("detail", n.getDetail());
			m.put("link", n.getLink());
			m.put("eventDate", n.getEventDate());
			m.put("createdAt", n.getCreatedAt() == null ? null : n.getCreatedAt().toString());
			out.add(m);
			if (out.size() >= 8) break;
		}
		return out;
	}

	/* ── helpers ────────────────────────────────────────────────────────── */

	private static String fmt(double v) {
		return Math.abs(v) >= 10 ? String.valueOf(Math.round(v))
			: String.valueOf(Math.round(v * 10) / 10.0);
	}
	private static String signed(double v) {
		String s = Math.abs(v) >= 10 ? String.valueOf(Math.round(v)) : String.valueOf(Math.round(v * 10) / 10.0);
		return (v > 0 ? "+" : "") + s;
	}
	private static Double dbl(Object o) {
		if (o == null) return null;
		if (o instanceof Number) return ((Number) o).doubleValue();
		try { return Double.parseDouble(o.toString()); } catch (Exception e) { return null; }
	}
	private static String str(Object o) { return o == null ? null : o.toString(); }
}
