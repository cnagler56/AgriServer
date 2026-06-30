package com.home.Domain;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * An admin-entered USDA report release date. The release-day burst refreshes a
 * report only when today matches one of its dates, so the data lands within
 * minutes of release without hard-coding USDA's shifting calendar — the admin
 * just keeps the list current from USDA's published schedule.
 */
@Entity
@Table(name = "report_release_date",
       uniqueConstraints = @UniqueConstraint(columnNames = {"report_key", "release_date"}))
public class ReportReleaseDate {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@JsonProperty
	private Long id;

	/** CROP_PRODUCTION / WASDE / GRAIN_STOCKS */
	@JsonProperty
	private String reportKey;

	@JsonProperty
	private LocalDate releaseDate;

	public Long getId() { return id; }
	public void setId(Long id) { this.id = id; }

	public String getReportKey() { return reportKey; }
	public void setReportKey(String reportKey) { this.reportKey = reportKey; }

	public LocalDate getReleaseDate() { return releaseDate; }
	public void setReleaseDate(LocalDate releaseDate) { this.releaseDate = releaseDate; }
}
