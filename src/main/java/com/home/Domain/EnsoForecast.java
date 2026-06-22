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
 * One season of the CPC/IRI probabilistic ENSO outlook — admin-entered, since
 * NOAA doesn't publish it as a clean machine-readable feed. e.g.
 * "JJA 2026 → El Niño 55% / Neutral 43% / La Niña 2%".
 */
@Entity
@Table(name = "enso_forecast")
public class EnsoForecast {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@JsonProperty
	private Long id;

	@JsonProperty
	private Integer seq;          // display order (soonest season first)

	@JsonProperty
	private String season;        // 3-month label, e.g. "JJA 2026"

	@JsonProperty
	private Double elNino;        // probability %, 0–100

	@JsonProperty
	private Double neutral;       // probability %

	@JsonProperty
	private Double laNina;        // probability %

	@JsonProperty
	private String issued;        // issuance label shared by the set, e.g. "June 2026"

	@JsonProperty
	private LocalDateTime updatedAt;

	@PrePersist
	void onCreate() {
		if (updatedAt == null) updatedAt = LocalDateTime.now();
	}

	public Long getId() { return id; }
	public void setId(Long id) { this.id = id; }

	public Integer getSeq() { return seq; }
	public void setSeq(Integer seq) { this.seq = seq; }

	public String getSeason() { return season; }
	public void setSeason(String season) { this.season = season; }

	public Double getElNino() { return elNino; }
	public void setElNino(Double elNino) { this.elNino = elNino; }

	public Double getNeutral() { return neutral; }
	public void setNeutral(Double neutral) { this.neutral = neutral; }

	public Double getLaNina() { return laNina; }
	public void setLaNina(Double laNina) { this.laNina = laNina; }

	public String getIssued() { return issued; }
	public void setIssued(String issued) { this.issued = issued; }

	public LocalDateTime getUpdatedAt() { return updatedAt; }
	public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
