package com.home.Service;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ApiResponse {
    private List<ApiItem> data;

    public List<ApiItem> getData() {
        return data;
    }

    public void setData(List<ApiItem> data) {
        this.data = data;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ApiItem {
    	@JsonProperty("commodity_desc")
        private String commodity;

    	@JsonProperty("short_desc")
        private String shortDesc;

    	@JsonProperty("state_name")
        private String state;

        @JsonProperty("year")
        private Integer year;

        @JsonProperty("Value")
        private String yield;

        @JsonProperty("unit_desc")
        private String unitDesc;

        @JsonProperty("reference_period_desc")
        private String referencePeriodDesc;

        @JsonProperty("week_ending")
        private String weekEnding;

        private String load_time;

        public String getCommodity() {
            return commodity;
        }

        public void setCommodity(String commodity) {
            this.commodity = commodity;
        }

        public String getShortDesc() {
            return shortDesc;
        }

        public void setShortDesc(String shortDesc) {
            this.shortDesc = shortDesc;
        }

        public String getState() {
            return state;
        }

        public void setState(String state) {
            this.state = state;
        }

        public Integer getYear() {
            return year;
        }

        public void setYear(Integer year) {
            this.year = year;
        }

        public String getUnitDesc() { return unitDesc; }
        public void setUnitDesc(String unitDesc) { this.unitDesc = unitDesc; }

        public String getReferencePeriodDesc() { return referencePeriodDesc; }
        public void setReferencePeriodDesc(String referencePeriodDesc) { this.referencePeriodDesc = referencePeriodDesc; }

        public String getWeekEnding() { return weekEnding; }
        public void setWeekEnding(String weekEnding) { this.weekEnding = weekEnding; }

        public String getYield() {
            return yield;
        }

        public void setValue(String yield) {
            this.yield = yield;
        }

        public String getLoad_time() {
            return load_time;
        }

        public void setLoad_time(String load_time) {
            this.load_time = load_time;
        }
    }
}

