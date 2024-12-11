package com.home.Domain;

public class WeatherResponse {

	private String name;
    private Long dayForecast;
    private Double temperature;
    private Double precipitationChance;  
    private String windSpeed;
    private String windDirection;
    private String shortForecast;
    private String fullForecast;

    // Constructor
    public WeatherResponse() {}

    public WeatherResponse(String name, Long dayForecast, Double temperature, Double precipitationChance, 
                           String windSpeed, String windDirection, 
                           String shortForecast, String fullForecast) {
    	this.name = name;
        this.dayForecast = dayForecast;
        this.temperature = temperature;
        this.precipitationChance = precipitationChance;
        this.windSpeed = windSpeed;
        this.windDirection = windDirection;
        this.shortForecast = shortForecast;
        this.fullForecast = fullForecast;
    }

    public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	// Getters and Setters
    public Long getDayForecast() {
        return dayForecast;
    }

    public void setDayForecast(Long dayForecast) {
        this.dayForecast = dayForecast;
    }

    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

    public Double getPrecipitationChance() {
        return precipitationChance;
    }

    public void setPrecipitationChance(Double precipitationChance) {
        this.precipitationChance = precipitationChance;
    }

    public String getWindSpeed() {
        return windSpeed;
    }

    public void setWindSpeed(String windSpeed) {
        this.windSpeed = windSpeed;
    }

    public String getWindDirection() {
        return windDirection;
    }

    public void setWindDirection(String windDirection) {
        this.windDirection = windDirection;
    }

    public String getShortForecast() {
        return shortForecast;
    }

    public void setShortForecast(String shortForecast) {
        this.shortForecast = shortForecast;
    }

    public String getFullForecast() {
        return fullForecast;
    }

    public void setFullForecast(String fullForecast) {
        this.fullForecast = fullForecast;
    }
}

