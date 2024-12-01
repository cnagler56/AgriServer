package com.home.Service;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ApiResponse {
    private List<ApiItem> data;

    public List<ApiItem> getData() {
        return data;
    }

    public void setData(List<ApiItem> data) {
        this.data = data;
    }

    public static class ApiItem {
    	@JsonProperty("commodity_desc")
        private String commodity;
    	
    	@JsonProperty("state_name")
        private String state;
        
        @JsonProperty("Value")
        private String yield;
        
        private String load_time;

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

