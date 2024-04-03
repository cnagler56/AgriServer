package com.home.Domain;

import java.util.ArrayList;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
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
	
	@ElementCollection
    @CollectionTable(name="this")
    private  ArrayList <String> yieldValues = new ArrayList<String>();
	
	@JsonProperty
	private Long userId;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public ArrayList<String> getYieldValues() {
		return yieldValues;
	}

	public void setYieldValues(ArrayList<String> yieldValues) {
		this.yieldValues = yieldValues;
	}

	public Long getUserId() {
		return userId;
	}

	public void setUserId(Long userId) {
		this.userId = userId;
	}
    



        

}
