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
 * Community-submitted yield estimate for the USDA Reports page.
 *
 * Generic across commodities (CORN, SOYBEANS, WHEAT, …) so we have one table
 * and one endpoint pair instead of one per commodity. Replaces the older
 * CornGuess / BeanGuess that had incompatible shapes.
 */
@Entity
@Table(name = "yield_guess")
public class YieldGuess {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@JsonProperty
	private Long id;

	@JsonProperty
	private String commodity;       // "CORN" / "SOYBEANS" / "WHEAT"

	@JsonProperty
	private Integer year;           // crop year the guess targets (set on save)

	@JsonProperty
	private Double estimate;        // National yield estimate in bu/acre

	@JsonProperty
	private String name;

	@JsonProperty
	private String state;

	@JsonProperty
	private String interest;        // "Farmer" / "Analyst" / "Trader" etc.

	@JsonProperty
	private Long userId;

	@JsonProperty
	private LocalDateTime date;

	/**
	 * Whether this guess is shown in the public Community Guesses list. A
	 * "cowardly" submit sets this false — the guess still counts toward the
	 * contest results, it's just hidden from the public roster.
	 */
	@JsonProperty
	private Boolean shared;

	@PrePersist
	void onCreate() {
		if (date == null) date = LocalDateTime.now();
		if (year == null) year = java.time.Year.now().getValue();
		if (shared == null) shared = Boolean.TRUE;
	}

	public Long getId() { return id; }
	public void setId(Long id) { this.id = id; }

	public String getCommodity() { return commodity; }
	public void setCommodity(String commodity) { this.commodity = commodity; }

	public Integer getYear() { return year; }
	public void setYear(Integer year) { this.year = year; }

	public Double getEstimate() { return estimate; }
	public void setEstimate(Double estimate) { this.estimate = estimate; }

	public String getName() { return name; }
	public void setName(String name) { this.name = name; }

	public String getState() { return state; }
	public void setState(String state) { this.state = state; }

	public String getInterest() { return interest; }
	public void setInterest(String interest) { this.interest = interest; }

	public Long getUserId() { return userId; }
	public void setUserId(Long userId) { this.userId = userId; }

	public LocalDateTime getDate() { return date; }
	public void setDate(LocalDateTime date) { this.date = date; }

	public Boolean getShared() { return shared; }
	public void setShared(Boolean shared) { this.shared = shared; }
}
