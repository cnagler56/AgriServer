package com.home.Domain;

import jakarta.persistence.*;

import java.sql.Timestamp;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@Entity
//@Table(name="corn_yield_data")
@JsonIgnoreProperties(ignoreUnknown = true)
public class NASSYieldData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @JsonProperty("commodity_desc")
    private String grain;

    public String getGrain() {
		return grain;
	}

	public void setGrain(String grain) {
		this.grain = grain;
	}
	@JsonProperty("load_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSS")
    private Timestamp loadTime;

    @JsonProperty("state_name")
    private String stateName;

    @JsonProperty("Value")
    @Column(name="yield", nullable = true)
    private String yield;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Timestamp getLoadTime() {
		return loadTime;
	}

	public void setLoadTime(Timestamp loadTime) {
		this.loadTime = loadTime;
	}

	public String getStateName() {
		return stateName;
	}

	public void setStateName(String stateName) {
		this.stateName = stateName;
	}

	public String getYield() {
		return yield;
	}

	public void setYield(String yield) {
		this.yield = yield;
	}
    @Override
    public String toString() {
        return "CornYieldData{id=" + id + ", loadTime=" + loadTime + ", stateName='" + stateName + "', yield=" + yield + '}';
    }
}

