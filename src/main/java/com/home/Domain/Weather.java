package com.home.Domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
@JsonIgnoreProperties(ignoreUnknown = true)
public class Weather {

	 @Id
	    @GeneratedValue(strategy = GenerationType.IDENTITY)
	    private Long id;
	 
	 	@JsonProperty("name")
	 	private String name;

	    @JsonProperty("number")
	    private Long dayForecast;

	    @JsonProperty("temperature")
	    private Double temperature;

	    @Embedded
	    @JsonProperty("probabilityOfPrecipitation")
	    private Precipitation precipitation;

	    @JsonProperty("windSpeed")
	    private String windSpeed;

	    @JsonProperty("windDirection")
	    private String windDirection;

	    @JsonProperty("shortForecast")
	    private String shortForecast;

	    @JsonProperty("detailedForecast")
	    private String fullForecast;

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

	    public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public Precipitation getPrecipitation() {
	        return precipitation;
	    }

	    public void setPrecipitation(Precipitation precipitation) {
	        this.precipitation = precipitation;
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

	    // Inner class for precipitation data
	    @Embeddable
	    public static class Precipitation {

	        @JsonProperty("unitCode")
	        private String unitCode;

	        @JsonProperty("value")
	        private Double value;

	        // Getters and Setters
	        public String getUnitCode() {
	            return unitCode;
	        }

	        public void setUnitCode(String unitCode) {
	            this.unitCode = unitCode;
	        }

	        public Double getValue() {
	            return value;
	        }

	        public void setValue(Double value) {
	            this.value = value;
	        }
	    }
	}
