package com.home.Domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * A field/farm record the user owns. Used as the anchor for downstream
 * features — days since planting, GDD accumulation, per-field history.
 */
@Entity
@Table(name = "fields")
public class Field {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@JsonProperty
	private Long id;

	@JsonProperty
	private Long userId;

	@JsonProperty
	private String name;          // e.g. "Home 40", "Back 80"

	@JsonProperty
	private Double acres;

	/** "CORN", "SOYBEANS", "WHEAT", "HAY", "PASTURE", "OTHER" */
	@JsonProperty
	private String crop;

	@JsonProperty
	private String variety;       // hybrid / variety name (optional)

	@JsonProperty
	private LocalDate plantedOn;

	/** Coordinates for weather / soil-moisture / GDD lookups (optional). */
	@JsonProperty
	private Double lat;

	@JsonProperty
	private Double lon;

	@JsonProperty
	@Lob
	@Column(columnDefinition = "TEXT")
	private String notes;

	@JsonProperty
	private LocalDateTime createdAt;

	@PrePersist
	void onCreate() {
		if (this.createdAt == null) this.createdAt = LocalDateTime.now();
	}

	public Long getId() { return id; }
	public void setId(Long id) { this.id = id; }

	public Long getUserId() { return userId; }
	public void setUserId(Long userId) { this.userId = userId; }

	public String getName() { return name; }
	public void setName(String name) { this.name = name; }

	public Double getAcres() { return acres; }
	public void setAcres(Double acres) { this.acres = acres; }

	public String getCrop() { return crop; }
	public void setCrop(String crop) { this.crop = crop; }

	public String getVariety() { return variety; }
	public void setVariety(String variety) { this.variety = variety; }

	public LocalDate getPlantedOn() { return plantedOn; }
	public void setPlantedOn(LocalDate plantedOn) { this.plantedOn = plantedOn; }

	public Double getLat() { return lat; }
	public void setLat(Double lat) { this.lat = lat; }

	public Double getLon() { return lon; }
	public void setLon(Double lon) { this.lon = lon; }

	public String getNotes() { return notes; }
	public void setNotes(String notes) { this.notes = notes; }

	public LocalDateTime getCreatedAt() { return createdAt; }
	public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
