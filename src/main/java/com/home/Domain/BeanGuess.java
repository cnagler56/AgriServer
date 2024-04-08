package com.home.Domain;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class BeanGuess {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@JsonProperty
	private Long id;
	
	@JsonProperty
	private int nationalGuess;
	
	@JsonProperty
	private int myState;
	
	@JsonProperty
	private Long userId;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public int getNationalGuess() {
		return nationalGuess;
	}

	public void setNationalGuess(int nationalGuess) {
		this.nationalGuess = nationalGuess;
	}

	public int getMyState() {
		return myState;
	}

	public void setMyState(int myState) {
		this.myState = myState;
	}

	public Long getUserId() {
		return userId;
	}

	public void setUserId(Long userId) {
		this.userId = userId;
	}
	
	
}
