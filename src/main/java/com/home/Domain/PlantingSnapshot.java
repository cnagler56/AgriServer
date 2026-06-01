package com.home.Domain;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Cached USDA NASS Area Planted datapoint for one (commodity, state, year, reference_period).
 *
 * NASS publishes two main planted-acreage reports per year:
 *   - Prospective Plantings (March 31)  → intended acreage
 *   - Acreage (June 30)                 → refined estimate of actual planted acres
 * Both come through with statisticcat_desc = AREA PLANTED.
 */
@Entity
@Table(
	name = "planting_snapshot",
	uniqueConstraints = @UniqueConstraint(columnNames = {"commodity", "state", "year", "referencePeriod"})
)
public class PlantingSnapshot {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@JsonProperty
	private Long id;

	@JsonProperty
	private String commodity;

	@JsonProperty
	private String state;

	@JsonProperty
	private Integer year;

	@JsonProperty
	@Column(name = "referencePeriod", length = 32)
	private String referencePeriod;

	/** Acres planted. */
	@JsonProperty
	private Long acres;

	@JsonProperty
	private LocalDateTime nassLoadTime;

	@JsonProperty
	private LocalDateTime fetchedAt;

	public Long getId() { return id; }
	public void setId(Long id) { this.id = id; }

	public String getCommodity() { return commodity; }
	public void setCommodity(String commodity) { this.commodity = commodity; }

	public String getState() { return state; }
	public void setState(String state) { this.state = state; }

	public Integer getYear() { return year; }
	public void setYear(Integer year) { this.year = year; }

	public String getReferencePeriod() { return referencePeriod; }
	public void setReferencePeriod(String referencePeriod) { this.referencePeriod = referencePeriod; }

	public Long getAcres() { return acres; }
	public void setAcres(Long acres) { this.acres = acres; }

	public LocalDateTime getNassLoadTime() { return nassLoadTime; }
	public void setNassLoadTime(LocalDateTime nassLoadTime) { this.nassLoadTime = nassLoadTime; }

	public LocalDateTime getFetchedAt() { return fetchedAt; }
	public void setFetchedAt(LocalDateTime fetchedAt) { this.fetchedAt = fetchedAt; }
}
