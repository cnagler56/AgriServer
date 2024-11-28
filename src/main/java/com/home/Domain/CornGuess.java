package com.home.Domain;



import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class CornGuess {
	
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@JsonProperty
	private Long id;
	
	@JsonProperty
	String grain;
	
	@JsonProperty
	@Column(name="date")
	LocalDateTime date;
		
	@JsonProperty
	@Column(name="yield_guess")
	public String yiel;
	
	@JsonProperty
	public String name;
	
	@JsonProperty
	public String state;
	
	@JsonProperty
	public String interest;
	
	@JsonProperty
	private Long userId;
	

	
	
	

	public String getGrain() {
		return grain;
	}

	public void setGrain(String grain) {
		this.grain = grain;
	}



	public String getYiel() {
		return yiel;
	}

	public void setYiel(String yiel) {
		this.yiel = yiel;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getState() {
		return state;
	}

	public void setState(String state) {
		this.state = state;
	}

	public String getInterest() {
		return interest;
	}

	public void setInterest(String interest) {
		this.interest = interest;
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}


	public Long getUserId() {
		return userId;
	}

	public void setUserId(Long userId) {
		this.userId = userId;
	}


	}
    



        


