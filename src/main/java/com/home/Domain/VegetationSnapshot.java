package com.home.Domain;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * One county's mean Vegetation Health Index for one NOAA VHP week, aggregated
 * from the 4km satellite composite. History is kept per week so trends can be
 * charted later; the API serves the newest week.
 */
@Entity
@Table(name = "vegetation_snapshot",
       uniqueConstraints = @UniqueConstraint(columnNames = {"fips", "year", "week"}),
       indexes = @Index(name = "idx_veg_year_week", columnList = "year, week"))
public class VegetationSnapshot {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@JsonProperty
	private Long id;

	/** 5-digit county FIPS, matching the frontend's county geojson ids. */
	@JsonProperty
	private String fips;

	@JsonProperty
	private String countyName;

	@JsonProperty
	private Integer year;

	/** NOAA VHP week number (1–52, 7-day periods from Jan 1). */
	@JsonProperty
	private Integer week;

	/** Mean VHI over valid (0–100) cells inside the county. */
	@JsonProperty
	private Double vhi;

	/** How many 4km cells contributed — a quality hint. */
	@JsonProperty
	private Integer cells;

	@JsonProperty
	private LocalDateTime fetchedAt;

	public Long getId() { return id; }
	public void setId(Long id) { this.id = id; }

	public String getFips() { return fips; }
	public void setFips(String fips) { this.fips = fips; }

	public String getCountyName() { return countyName; }
	public void setCountyName(String countyName) { this.countyName = countyName; }

	public Integer getYear() { return year; }
	public void setYear(Integer year) { this.year = year; }

	public Integer getWeek() { return week; }
	public void setWeek(Integer week) { this.week = week; }

	public Double getVhi() { return vhi; }
	public void setVhi(Double vhi) { this.vhi = vhi; }

	public Integer getCells() { return cells; }
	public void setCells(Integer cells) { this.cells = cells; }

	public LocalDateTime getFetchedAt() { return fetchedAt; }
	public void setFetchedAt(LocalDateTime fetchedAt) { this.fetchedAt = fetchedAt; }
}
