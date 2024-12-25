package com.home.Domain;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.persistence.Access;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

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
	    
	    @CreationTimestamp
	    @Column(updatable = false) 
	    private LocalDateTime createdAt;
	    
	    public Weather() {}

	    public Weather(String name, Long dayForecast, double temperature, Precipitation precipitation, String windSpeed, String windDirection, String shortForecast, String fullForecast) {
	        this.name = name;
	        this.dayForecast = dayForecast;
	        this.temperature = temperature;
	        this.precipitation = precipitation;
	        this.windSpeed = windSpeed;
	        this.windDirection = windDirection;
	        this.shortForecast = shortForecast;
	        this.fullForecast = fullForecast;
	    }

	    public LocalDateTime getCreatedAt() {
	        return createdAt;
	    }
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

	    @Transient
	    public Long getPrecipitationValue() {
	        return (precipitation != null) ? precipitation.getValue() : null;
	    }
	    
//	    @Transient
//	    public Long getPrecipitationUnitCode() {
//	        return (precipitation != null) ? precipitation.getUnitCode() : null;
//	    }

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

	 
	    
	    @Embeddable
	    public static class Precipitation {
	    	
	    	public Precipitation() {}

	        @JsonProperty("unitCode")
	        private String unitCode;

	        @JsonProperty("value")
	        private Long value;

	        public Precipitation(Long value) {
//	            this.unitCode = unitCode;
	            this.value = value;
	        }

	        public Long getValue() {
	            return value;
	        }

	        public void setValue(Long value) {
	            this.value = value;
	        }
	        
	        public String getUnitCode() {
	            return unitCode;
	        }

	        public void setUnitCode(String unitCode) {
	            this.unitCode = unitCode;
	        }
	    }
	}
