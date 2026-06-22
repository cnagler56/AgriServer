package com.home.Domain;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * One line of a USDA supply/demand (WASDE / PSD) balance sheet — e.g.
 * "Corn, United States, 2025/26, Production = 432,342 (1000 MT)".
 *
 * Sourced from the USDA FAS PSD API and cached here so the dashboards don't
 * call USDA on every page view. Re-ingested monthly after each WASDE release.
 */
@Entity
@Table(name = "supply_demand")
public class SupplyDemand {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@JsonProperty
	private Long id;

	@JsonProperty
	private String commodity;      // "CORN" / "SOYBEANS" / "WHEAT"

	@JsonProperty
	private String region;         // "US" or "WORLD"

	@JsonProperty
	private Integer marketYear;    // begin year, e.g. 2025 → "2025/26"

	@JsonProperty
	private Integer month;         // PSD release month of this snapshot

	@JsonProperty
	private Integer seq;           // row order within (commodity, region) for display

	@JsonProperty
	private String attribute;      // "Production", "Ending Stocks", …

	@JsonProperty
	private Double value;

	@JsonProperty
	private String unit;           // WASDE units, e.g. "Million Bushels", "Bushels"

	@JsonProperty
	private String reportDate;     // WASDE report date, e.g. "2026-06-12"

	@JsonProperty
	private LocalDateTime updatedAt;

	@PrePersist
	void onCreate() {
		if (updatedAt == null) updatedAt = LocalDateTime.now();
	}

	public Long getId() { return id; }
	public void setId(Long id) { this.id = id; }

	public String getCommodity() { return commodity; }
	public void setCommodity(String commodity) { this.commodity = commodity; }

	public String getRegion() { return region; }
	public void setRegion(String region) { this.region = region; }

	public Integer getMarketYear() { return marketYear; }
	public void setMarketYear(Integer marketYear) { this.marketYear = marketYear; }

	public Integer getMonth() { return month; }
	public void setMonth(Integer month) { this.month = month; }

	public Integer getSeq() { return seq; }
	public void setSeq(Integer seq) { this.seq = seq; }

	public String getAttribute() { return attribute; }
	public void setAttribute(String attribute) { this.attribute = attribute; }

	public Double getValue() { return value; }
	public void setValue(Double value) { this.value = value; }

	public String getUnit() { return unit; }
	public void setUnit(String unit) { this.unit = unit; }

	public String getReportDate() { return reportDate; }
	public void setReportDate(String reportDate) { this.reportDate = reportDate; }

	public LocalDateTime getUpdatedAt() { return updatedAt; }
	public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
