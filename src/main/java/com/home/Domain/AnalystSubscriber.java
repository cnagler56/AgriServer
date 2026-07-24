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
 * A customer the analysis provider has granted access to. Access is by email:
 * once someone signs in with an account whose email is on an analyst's list,
 * they can read that analyst's published posts on the Analysis tab.
 */
@Entity
@Table(name = "analyst_subscriber",
       uniqueConstraints = @UniqueConstraint(columnNames = {"analyst_user_id", "email"}))
public class AnalystSubscriber {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@JsonProperty
	private Long id;

	/** Which provider owns this subscriber entry. */
	@JsonProperty
	private Long analystUserId;

	/** Lower-cased subscriber email. */
	@JsonProperty
	private String email;

	/** Optional label the provider adds (e.g. the customer's name/farm). */
	@JsonProperty
	private String note;

	@JsonProperty
	private LocalDateTime addedAt;

	public Long getId() { return id; }
	public void setId(Long id) { this.id = id; }

	public Long getAnalystUserId() { return analystUserId; }
	public void setAnalystUserId(Long analystUserId) { this.analystUserId = analystUserId; }

	public String getEmail() { return email; }
	public void setEmail(String email) { this.email = email; }

	public String getNote() { return note; }
	public void setNote(String note) { this.note = note; }

	public LocalDateTime getAddedAt() { return addedAt; }
	public void setAddedAt(LocalDateTime addedAt) { this.addedAt = addedAt; }
}
