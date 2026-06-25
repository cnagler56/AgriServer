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
 * Our own monthly snapshot of CONAB's national production estimate. CONAB's free
 * series file only carries the latest number (no per-month history), so we record
 * one row per (commodity, crop year, month) to derive month-over-month change as
 * the season's estimates evolve.
 */
@Entity
@Table(name = "conab_snapshot")
public class ConabSnapshot {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@JsonProperty
	private Long id;

	@JsonProperty
	private String commodity;     // "CORN" / "SOYBEANS" / "WHEAT"

	@JsonProperty
	private String cropYear;      // "2025/26"

	@JsonProperty
	private Integer monthKey;     // YYYYMM of when we captured it

	@JsonProperty
	private Double production;    // national production, thousand tonnes

	@JsonProperty
	private LocalDateTime createdAt;

	@PrePersist
	void onCreate() {
		if (createdAt == null) createdAt = LocalDateTime.now();
	}

	public Long getId() { return id; }
	public void setId(Long id) { this.id = id; }

	public String getCommodity() { return commodity; }
	public void setCommodity(String commodity) { this.commodity = commodity; }

	public String getCropYear() { return cropYear; }
	public void setCropYear(String cropYear) { this.cropYear = cropYear; }

	public Integer getMonthKey() { return monthKey; }
	public void setMonthKey(Integer monthKey) { this.monthKey = monthKey; }

	public Double getProduction() { return production; }
	public void setProduction(Double production) { this.production = production; }

	public LocalDateTime getCreatedAt() { return createdAt; }
	public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
