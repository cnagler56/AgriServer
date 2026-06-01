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
 * Cached USDA NASS yield datapoint for one (commodity, state, year, reference_period).
 * Generalized successor to CornYieldSnapshot — same shape but with a commodity column,
 * so a single table backs Corn / Soybeans / Wheat / etc.
 */
@Entity
@Table(
	name = "yield_snapshot",
	uniqueConstraints = @UniqueConstraint(columnNames = {"commodity", "state", "year", "referencePeriod"})
)
public class YieldSnapshot {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@JsonProperty
	private Long id;

	/** "CORN" / "SOYBEANS" / "WHEAT" — the NASS commodity_desc. */
	@JsonProperty
	private String commodity;

	@JsonProperty
	private String state;

	@JsonProperty
	private Integer year;

	/** "AUG" / "SEP" / "OCT" / "NOV" / "YEAR". */
	@JsonProperty
	@Column(name = "referencePeriod", length = 32)
	private String referencePeriod;

	/** bu/acre (or whatever NASS reports for the commodity). */
	@JsonProperty
	private Double yieldBu;

	/** Final area harvested in acres, attached from a separate NASS query. */
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

	public Double getYieldBu() { return yieldBu; }
	public void setYieldBu(Double yieldBu) { this.yieldBu = yieldBu; }

	public Long getAcres() { return acres; }
	public void setAcres(Long acres) { this.acres = acres; }

	public LocalDateTime getNassLoadTime() { return nassLoadTime; }
	public void setNassLoadTime(LocalDateTime nassLoadTime) { this.nassLoadTime = nassLoadTime; }

	public LocalDateTime getFetchedAt() { return fetchedAt; }
	public void setFetchedAt(LocalDateTime fetchedAt) { this.fetchedAt = fetchedAt; }
}
