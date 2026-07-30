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
 * One X (Twitter) promotional post — attempted or actually published. Doubles as
 * the log the admin panel shows and as the rotation cursor: the most recent
 * POSTED/DRYRUN row tells the scheduler which page to promote next.
 */
@Entity
@Table(name = "social_post")
public class SocialPost {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@JsonProperty
	private Long id;

	/** Rotation key of the promoted page (matches SocialPostService.Page.key). */
	@JsonProperty
	private String pageKey;

	@JsonProperty
	private String pageLabel;

	@Column(length = 600)
	@JsonProperty
	private String url;

	/** The exact text sent to X (hook + link + hashtags). */
	@Column(length = 600)
	@JsonProperty
	private String text;

	/** POSTED · DRYRUN · FAILED */
	@JsonProperty
	private String status;

	/** MORNING · AFTERNOON · MANUAL */
	@JsonProperty
	private String slot;

	/** X tweet id when POSTED. */
	@JsonProperty
	private String tweetId;

	@Column(length = 600)
	@JsonProperty
	private String note;

	@JsonProperty
	private LocalDateTime createdAt;

	public Long getId() { return id; }
	public void setId(Long id) { this.id = id; }

	public String getPageKey() { return pageKey; }
	public void setPageKey(String pageKey) { this.pageKey = pageKey; }

	public String getPageLabel() { return pageLabel; }
	public void setPageLabel(String pageLabel) { this.pageLabel = pageLabel; }

	public String getUrl() { return url; }
	public void setUrl(String url) { this.url = url; }

	public String getText() { return text; }
	public void setText(String text) { this.text = text; }

	public String getStatus() { return status; }
	public void setStatus(String status) { this.status = status; }

	public String getSlot() { return slot; }
	public void setSlot(String slot) { this.slot = slot; }

	public String getTweetId() { return tweetId; }
	public void setTweetId(String tweetId) { this.tweetId = tweetId; }

	public String getNote() { return note; }
	public void setNote(String note) { this.note = note; }

	public LocalDateTime getCreatedAt() { return createdAt; }
	public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
