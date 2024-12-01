package com.home.Domain;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
@JsonIgnoreProperties(ignoreUnknown = true)
public class NASSYieldData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @JsonProperty("commodity_desc")
    private String commodity;
    
    @JsonProperty("state_name")
    private String state;
    
    @JsonProperty("Value")
    private String yield;  
    
    
    private String acresValue;  
    
    @JsonProperty("load_time")
    private String loadTime;

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCommodity() { return commodity; }
    public void setCommodity(String commodity) { this.commodity = commodity; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public String getYield() { return yield; }
    public void setYield(String yieldValue) { this.yield = yield; }
    public String getAcresValue() { return acresValue; }
    public void setAcresValue(String acresValue) { this.acresValue = acresValue; }
    public String getLoadTime() { return loadTime; }
    public void setLoadTime(String loadTime) { this.loadTime = loadTime; }
}

