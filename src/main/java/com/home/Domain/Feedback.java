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
 * A message left on the Contact Us page. When the sender is signed in we stamp
 * their userId / name / email automatically; anonymous visitors can type their
 * own name + email.
 */
@Entity
@Table(name = "feedback")
public class Feedback {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@JsonProperty
	private Long id;

	@JsonProperty
	private Long userId;        // null when submitted by a logged-out visitor

	@JsonProperty
	private String name;

	@JsonProperty
	private String email;

	@JsonProperty
	@Column(length = 4000)
	private String message;

	@JsonProperty
	private LocalDateTime date;

	@PrePersist
	void onCreate() {
		if (date == null) date = LocalDateTime.now();
	}

	public Long getId() { return id; }
	public void setId(Long id) { this.id = id; }

	public Long getUserId() { return userId; }
	public void setUserId(Long userId) { this.userId = userId; }

	public String getName() { return name; }
	public void setName(String name) { this.name = name; }

	public String getEmail() { return email; }
	public void setEmail(String email) { this.email = email; }

	public String getMessage() { return message; }
	public void setMessage(String message) { this.message = message; }

	public LocalDateTime getDate() { return date; }
	public void setDate(LocalDateTime date) { this.date = date; }
}
