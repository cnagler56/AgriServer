package com.home.Domain;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * One auto-generated "Latest News" item, distilled from the data feeds we already
 * ingest (WASDE, ethanol, ENSO, prices). Persisted so the 3-day shelf life is
 * measured from when the item was first seen — which survives restarts and the
 * varying release lag of each source. {@code dedupeKey} keeps us from posting the
 * same release twice.
 */
@Entity
@Table(name = "news_item")
public class NewsItem {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@JsonProperty
	private Long id;

	/** When we first generated this item — drives the 3-day roll-off. */
	@JsonProperty
	private LocalDateTime createdAt;

	@JsonProperty
	private String category;     // "ETHANOL" / "WASDE" / "ENSO" / "PRICE"

	@JsonProperty
	private String icon;         // emoji

	@JsonProperty
	@Column(length = 300)
	private String headline;

	@JsonProperty
	@Column(length = 300)
	private String detail;       // optional second line

	@JsonProperty
	private String link;         // in-app path, e.g. "/ethanol"

	@JsonProperty
	private String eventDate;    // human label of the underlying release, e.g. "Jun '26"

	/** Stable identity for one release (e.g. "ETHANOL|2026-06-12"); unique. */
	@Column(unique = true, length = 200)
	private String dedupeKey;

	@PrePersist
	void onCreate() {
		if (createdAt == null) createdAt = LocalDateTime.now();
	}

	public Long getId() { return id; }
	public void setId(Long id) { this.id = id; }

	public LocalDateTime getCreatedAt() { return createdAt; }
	public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

	public String getCategory() { return category; }
	public void setCategory(String category) { this.category = category; }

	public String getIcon() { return icon; }
	public void setIcon(String icon) { this.icon = icon; }

	public String getHeadline() { return headline; }
	public void setHeadline(String headline) { this.headline = headline; }

	public String getDetail() { return detail; }
	public void setDetail(String detail) { this.detail = detail; }

	public String getLink() { return link; }
	public void setLink(String link) { this.link = link; }

	public String getEventDate() { return eventDate; }
	public void setEventDate(String eventDate) { this.eventDate = eventDate; }

	public String getDedupeKey() { return dedupeKey; }
	public void setDedupeKey(String dedupeKey) { this.dedupeKey = dedupeKey; }
}
