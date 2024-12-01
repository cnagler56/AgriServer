package com.home.Domain;

import java.time.LocalDateTime;

public class CombinedYieldData {
    private String commodity;
    private String state;
    private Double yieldValue;
    private Double acresValue;
    private LocalDateTime loadTime;
	public String getCommodity() {
		return commodity;
	}
	public void setCommodity(String commodity) {
		this.commodity = commodity;
	}
	public String getState() {
		return state;
	}
	public void setState(String state) {
		this.state = state;
	}
	public Double getYieldValue() {
		return yieldValue;
	}
	public void setYieldValue(Double yieldValue) {
		this.yieldValue = yieldValue;
	}
	public Double getAcresValue() {
		return acresValue;
	}
	public void setAcresValue(Double acresValue) {
		this.acresValue = acresValue;
	}
	public LocalDateTime getLoadTime() {
		return loadTime;
	}
	public void setLoadTime(LocalDateTime loadTime) {
		this.loadTime = loadTime;
	}

    // Getters and setters
}

