package com.home.Domain;

import java.time.LocalDate;
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
 * One sampled reading of a CPC extended outlook: for one issuance date, one
 * range (6–10 or 8–14 day), one element (temp/precip), at one location (a
 * Midwest state centroid or the MIDWEST composite) — the outlook's category
 * and probability there. Three consecutive issuances make a trend.
 */
@Entity
@Table(name = "outlook_snapshot",
       uniqueConstraints = @UniqueConstraint(columnNames = {"issued_date", "range_key", "element", "location"}),
       indexes = @Index(name = "idx_outlook_series", columnList = "range_key, element, location, issued_date"))
public class OutlookSnapshot {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@JsonProperty
	private Long id;

	@JsonProperty
	private LocalDate issuedDate;

	/** "610" or "814". */
	@JsonProperty
	private String rangeKey;

	/** "TEMP" or "PRECIP". */
	@JsonProperty
	private String element;

	/** State abbreviation, or "MIDWEST" for the composite. */
	@JsonProperty
	private String location;

	/** "ABOVE", "BELOW", "NORMAL", or "EC" (equal chances / outside all polygons). */
	@JsonProperty
	private String category;

	/** CPC probability (33–90) for the category; null for EC. */
	@JsonProperty
	private Double prob;

	@JsonProperty
	private LocalDate validStart;

	@JsonProperty
	private LocalDate validEnd;

	@JsonProperty
	private LocalDateTime fetchedAt;

	public Long getId() { return id; }
	public void setId(Long id) { this.id = id; }

	public LocalDate getIssuedDate() { return issuedDate; }
	public void setIssuedDate(LocalDate issuedDate) { this.issuedDate = issuedDate; }

	public String getRangeKey() { return rangeKey; }
	public void setRangeKey(String rangeKey) { this.rangeKey = rangeKey; }

	public String getElement() { return element; }
	public void setElement(String element) { this.element = element; }

	public String getLocation() { return location; }
	public void setLocation(String location) { this.location = location; }

	public String getCategory() { return category; }
	public void setCategory(String category) { this.category = category; }

	public Double getProb() { return prob; }
	public void setProb(Double prob) { this.prob = prob; }

	public LocalDate getValidStart() { return validStart; }
	public void setValidStart(LocalDate validStart) { this.validStart = validStart; }

	public LocalDate getValidEnd() { return validEnd; }
	public void setValidEnd(LocalDate validEnd) { this.validEnd = validEnd; }

	public LocalDateTime getFetchedAt() { return fetchedAt; }
	public void setFetchedAt(LocalDateTime fetchedAt) { this.fetchedAt = fetchedAt; }
}
