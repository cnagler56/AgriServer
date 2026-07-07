package com.home.Domain;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * A single admin-editable site setting, stored as a key/value pair — e.g.
 * which animated banner the home page shows ({@code home_banner} → "corn").
 * Generic on purpose: future toggles get a new key, not a new table.
 */
@Entity
@Table(name = "site_setting",
       uniqueConstraints = @UniqueConstraint(columnNames = {"setting_key"}))
public class SiteSetting {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@JsonProperty
	private Long id;

	@JsonProperty
	private String settingKey;

	@JsonProperty
	private String settingValue;

	@JsonProperty
	private LocalDateTime updatedAt;

	public Long getId() { return id; }
	public void setId(Long id) { this.id = id; }

	public String getSettingKey() { return settingKey; }
	public void setSettingKey(String settingKey) { this.settingKey = settingKey; }

	public String getSettingValue() { return settingValue; }
	public void setSettingValue(String settingValue) { this.settingValue = settingValue; }

	public LocalDateTime getUpdatedAt() { return updatedAt; }
	public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
