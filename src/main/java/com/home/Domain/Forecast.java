package com.home.Domain;

public class Forecast {
    private String shortForecast; // A brief description of the weather (e.g., "Partly Cloudy")
    private Integer temperature;  // Temperature in Fahrenheit
    private String startTime;     // The starting time of the forecast period (ISO 8601 format)

    // Getters and Setters
    public String getShortForecast() {
        return shortForecast;
    }

    public void setShortForecast(String shortForecast) {
        this.shortForecast = shortForecast;
    }

    public Integer getTemperature() {
        return temperature;
    }

    public void setTemperature(Integer temperature) {
        this.temperature = temperature;
    }

    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    @Override
    public String toString() {
        return "Forecast{" +
                "shortForecast='" + shortForecast + '\'' +
                ", temperature=" + temperature +
                ", startTime='" + startTime + '\'' +
                '}';
    }
}     
