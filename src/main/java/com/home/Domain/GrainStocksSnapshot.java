package com.home.Domain;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * Durable last-good copy of one commodity's computed grain-stocks result. The
 * service keeps an in-memory cache for fast reads, but persists each successful
 * build here so a restart/redeploy (or a USDA outage on refresh) serves the last
 * known numbers instead of going blank. Payload is the built result as JSON.
 */
@Entity
@Table(name = "grain_stocks_snapshot")
public class GrainStocksSnapshot {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@JsonProperty
	private Long id;

	@Column(unique = true, nullable = false)
	@JsonProperty
	private String commodity;

	@Column(length = 4000)
	@JsonProperty
	private String payloadJson;

	@JsonProperty
	private LocalDateTime updatedAt;

	@PrePersist
	@PreUpdate
	void touch() {
		updatedAt = LocalDateTime.now();
	}

	public Long getId() { return id; }
	public void setId(Long id) { this.id = id; }

	public String getCommodity() { return commodity; }
	public void setCommodity(String commodity) { this.commodity = commodity; }

	public String getPayloadJson() { return payloadJson; }
	public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }

	public LocalDateTime getUpdatedAt() { return updatedAt; }
	public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
