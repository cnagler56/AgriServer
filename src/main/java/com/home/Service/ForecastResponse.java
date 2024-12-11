package com.home.Service;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.home.Domain.Weather;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ForecastResponse {
    private Properties properties;

    public Properties getProperties() {
        return properties;
    }

    public void setProperties(Properties properties) {
        this.properties = properties;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Properties {
        private List<Weather> periods;

        public List<Weather> getPeriods() {
            return periods;
        }

        public void setPeriods(List<Weather> periods) {
            this.periods = periods;
        }
    }
}
