package com.home.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.home.Domain.ReportReleaseDate;
import com.home.Repository.ReportReleaseDateRepository;

/**
 * Fires a release-time refresh burst, but only for reports whose admin-entered
 * release date matches today. USDA publishes its calendar in advance and the
 * exact day shifts month to month, so rather than hard-code dates the admin
 * keeps a list per report; this dispatches to the right service on those days.
 *
 * The burst runs at the noon-Eastern release window (12:01–12:25 ET); each
 * report's own daily/monthly schedules remain as a safety net if a date is
 * missed.
 */
@Service
public class ReportScheduleService {

	/** Reports whose release dates are admin-managed here. */
	public static final List<String> REPORTS = List.of("CROP_PRODUCTION", "WASDE", "GRAIN_STOCKS");

	private static final ZoneId EASTERN = ZoneId.of("America/New_York");

	private final ReportReleaseDateRepository dateRepo;
	private final UsdaReportsService usdaReports;
	private final SupplyDemandService supplyDemand;
	private final GrainStocksService grainStocks;

	public ReportScheduleService(ReportReleaseDateRepository dateRepo,
			UsdaReportsService usdaReports,
			SupplyDemandService supplyDemand,
			GrainStocksService grainStocks) {
		this.dateRepo = dateRepo;
		this.usdaReports = usdaReports;
		this.supplyDemand = supplyDemand;
		this.grainStocks = grainStocks;
	}

	/**
	 * Release-day burst at the noon-ET window: 12:01/04/08/15/25 ET. The first lands
	 * ~1 min after release; the later ones ride out USDA's ingestion lag. Refreshes a
	 * report only if today is in its admin date list; otherwise it's a cheap no-op.
	 */
	@Scheduled(cron = "0 1,4,8,15,25 11 * * *", zone = "America/Chicago")
	public void releaseBurst() {
		LocalDate today = LocalDate.now(EASTERN);
		if (isReleaseDay("CROP_PRODUCTION", today)) run("CROP_PRODUCTION", usdaReports::refreshMonthlyCropProduction);
		if (isReleaseDay("WASDE", today))           run("WASDE", supplyDemand::ingestAll);
		if (isReleaseDay("GRAIN_STOCKS", today))     run("GRAIN_STOCKS", grainStocks::refresh);
	}

	private boolean isReleaseDay(String key, LocalDate today) {
		return dateRepo.existsByReportKeyAndReleaseDate(key, today);
	}

	private void run(String key, Runnable refresh) {
		try {
			refresh.run();
			System.out.println("[REPORTS] release-day refresh fired for " + key);
		} catch (Exception e) {
			System.err.println("[REPORTS] " + key + " release-day refresh failed: "
				+ e.getClass().getSimpleName() + " - " + e.getMessage());
		}
	}

	/* ── admin management ───────────────────────────────────────────────── */

	public List<ReportReleaseDate> all() {
		return dateRepo.findAllByOrderByReportKeyAscReleaseDateAsc();
	}

	/** Replace one report's full set of dates with the supplied list. */
	@Transactional
	public List<ReportReleaseDate> setDates(String reportKey, List<LocalDate> dates) {
		String key = reportKey == null ? "" : reportKey.trim().toUpperCase();
		if (!REPORTS.contains(key)) {
			throw new IllegalArgumentException("Unknown report: " + reportKey);
		}
		dateRepo.deleteByReportKey(key);
		List<ReportReleaseDate> rows = new ArrayList<>();
		if (dates != null) {
			for (LocalDate d : dates.stream().filter(Objects::nonNull).distinct().sorted().toList()) {
				ReportReleaseDate row = new ReportReleaseDate();
				row.setReportKey(key);
				row.setReleaseDate(d);
				rows.add(row);
			}
		}
		return dateRepo.saveAll(rows);
	}
}
