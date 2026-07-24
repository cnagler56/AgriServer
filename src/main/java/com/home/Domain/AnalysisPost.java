package com.home.Domain;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One piece of market analysis authored by an ANALYST provider. Shown on the
 * gated Analysis tab to that provider's subscribers (and admins/the author).
 */
@Entity
@Table(name = "analysis_post")
public class AnalysisPost {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@JsonProperty
	private Long id;

	/** The ANALYST user who wrote it. */
	@JsonProperty
	private Long authorUserId;

	/** Display name snapshot, so the byline survives even if the account changes. */
	@JsonProperty
	private String authorName;

	@JsonProperty
	private String title;

	/**
	 * Markdown body. Explicit LONGTEXT: a bare @Lob let MySQL default the column
	 * to TINYTEXT (255 bytes), which truncated real reports and 500'd on save.
	 */
	@Column(columnDefinition = "LONGTEXT")
	@JsonProperty
	private String body;

	/** Drafts (false) are visible only to the author; published (true) to subscribers. */
	@JsonProperty
	private boolean published;

	@JsonProperty
	private LocalDateTime publishedAt;

	@JsonProperty
	private LocalDateTime updatedAt;

	public Long getId() { return id; }
	public void setId(Long id) { this.id = id; }

	public Long getAuthorUserId() { return authorUserId; }
	public void setAuthorUserId(Long authorUserId) { this.authorUserId = authorUserId; }

	public String getAuthorName() { return authorName; }
	public void setAuthorName(String authorName) { this.authorName = authorName; }

	public String getTitle() { return title; }
	public void setTitle(String title) { this.title = title; }

	public String getBody() { return body; }
	public void setBody(String body) { this.body = body; }

	public boolean isPublished() { return published; }
	public void setPublished(boolean published) { this.published = published; }

	public LocalDateTime getPublishedAt() { return publishedAt; }
	public void setPublishedAt(LocalDateTime publishedAt) { this.publishedAt = publishedAt; }

	public LocalDateTime getUpdatedAt() { return updatedAt; }
	public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
