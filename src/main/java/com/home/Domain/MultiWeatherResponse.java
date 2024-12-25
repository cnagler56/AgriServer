package com.home.Domain;

import java.util.List;

public class MultiWeatherResponse {
    private String location; // e.g., "MPX/107,71"
    private List<Forecast> forecasts; // List of forecasts for this location

    // Getters and Setters
    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public List<Forecast> getForecasts() {
        return forecasts;
    }

    public void setForecasts(List<Forecast> forecasts) {
        this.forecasts = forecasts;
    }

    @Override
    public String toString() {
        return "MultiWeatherResponse{" +
                "location='" + location + '\'' +
                ", forecasts=" + forecasts +
                '}';
    }
}

