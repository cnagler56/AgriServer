//package com.home.Domain;
//
//import com.fasterxml.jackson.annotation.JsonProperty;
//
//import jakarta.persistence.Entity;
//import jakarta.persistence.GeneratedValue;
//import jakarta.persistence.GenerationType;
//import jakarta.persistence.Id;
//
//@Entity
//public abstract class Grain {
//	@Id
//	@GeneratedValue(strategy=GenerationType.SEQUENCE)
//	@JsonProperty
//	Long id;
//	
//	@JsonProperty
//	String state;
//	
//	@JsonProperty
//	double yield;
//	
//	@JsonProperty
//	Integer acres;
//	
//	@JsonProperty
//	double avg;
//
//	public Long getId() {
//		return id;
//	}
//
//	public void setId(Long id) {
//		this.id = id;
//	}
//
//	public String getState() {
//		return state;
//	}
//
//	public void setState(String state) {
//		this.state = state;
//	}
//
//	public double getYield() {
//		return yield;
//	}
//
//	public void setYield(double yield) {
//		this.yield = yield;
//	}
//
//	public Integer getAcres() {
//		return acres;
//	}
//
//	public void setAcres(Integer acres) {
//		this.acres = acres;
//	}
//
//	public double getAvg() {
//		return avg;
//	}
//
//	public void setAvg(double avg) {
//		this.avg = avg;
//	}
//	
//	
//}
