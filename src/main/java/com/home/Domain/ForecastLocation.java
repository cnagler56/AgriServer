package com.home.Domain;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

/**
 * A tracked location for the "Change in Forecast" feature.
 *
 * We store two NWS forecast snapshots as JSON strings — the most recent fetch
 * (current) and the one before it (previous). When the user clicks Refresh:
 *   1. The existing `current` is moved into `previous`
 *   2. A fresh fetch is stored as the new `current`
 * The frontend then renders day-by-day deltas with color shading.
 *
 * Snapshots are persisted as TEXT JSON rather than a child table because:
 *   - We only ever care about the last two snapshots, never history
 *   - The structure is well-defined and consumed entirely by the same feature
 *   - Avoids a join + an extra entity for a non-shared shape
 */
@Entity
@Table(name = "forecast_locations")
public class ForecastLocation {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@JsonProperty
	private Long id;

	/** Display name, e.g. "Winnebago, MN" */
	@JsonProperty
	private String name;

	@JsonProperty
	private Double lat;

	@JsonProperty
	private Double lon;

	/** Cached NWS grid identifiers — looked up from lat/lon on first refresh. */
	@JsonProperty
	private String gridId;

	@JsonProperty
	private Integer gridX;

	@JsonProperty
	private Integer gridY;

	@JsonProperty
	private LocalDateTime currentFetchedAt;

	@JsonProperty
	@Lob
	@Column(columnDefinition = "TEXT")
	private String currentSnapshotJson;

	@JsonProperty
	private LocalDateTime previousFetchedAt;

	@JsonProperty
	@Lob
	@Column(columnDefinition = "TEXT")
	private String previousSnapshotJson;

	public Long getId() { return id; }
	public void setId(Long id) { this.id = id; }

	public String getName() { return name; }
	public void setName(String name) { this.name = name; }

	public Double getLat() { return lat; }
	public void setLat(Double lat) { this.lat = lat; }

	public Double getLon() { return lon; }
	public void setLon(Double lon) { this.lon = lon; }

	public String getGridId() { return gridId; }
	public void setGridId(String gridId) { this.gridId = gridId; }

	public Integer getGridX() { return gridX; }
	public void setGridX(Integer gridX) { this.gridX = gridX; }

	public Integer getGridY() { return gridY; }
	public void setGridY(Integer gridY) { this.gridY = gridY; }

	public LocalDateTime getCurrentFetchedAt() { return currentFetchedAt; }
	public void setCurrentFetchedAt(LocalDateTime currentFetchedAt) { this.currentFetchedAt = currentFetchedAt; }

	public String getCurrentSnapshotJson() { return currentSnapshotJson; }
	public void setCurrentSnapshotJson(String currentSnapshotJson) { this.currentSnapshotJson = currentSnapshotJson; }

	public LocalDateTime getPreviousFetchedAt() { return previousFetchedAt; }
	public void setPreviousFetchedAt(LocalDateTime previousFetchedAt) { this.previousFetchedAt = previousFetchedAt; }

	public String getPreviousSnapshotJson() { return previousSnapshotJson; }
	public void setPreviousSnapshotJson(String previousSnapshotJson) { this.previousSnapshotJson = previousSnapshotJson; }
}
