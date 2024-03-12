package com.home.Domain;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name="posts")
public class Post {

	@Id
	@GeneratedValue(strategy=GenerationType.SEQUENCE)
	@JsonProperty
	Long idposts;
	
	@JsonProperty
	String title;
	
	@JsonProperty
	String body;
	
	@JsonProperty
	String time;

	@JsonProperty
	Long userId;

	public Long getIdposts() {
		return idposts;
	}

	public void setIdposts(Long idposts) {
		this.idposts = idposts;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getBody() {
		return body;
	}

	public void setBody(String body) {
		this.body = body;
	}

	public String getTime() {
		return time;
	}

	public void setTime(String time) {
		this.time = time;
	}

	public Long getUserId() {
		return userId;
	}

	public void setUserId(Long userId) {
		this.userId = userId;
	}
	
}
