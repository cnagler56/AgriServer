package com.home.Service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.home.Domain.NASSYieldData;

import java.util.List;

public class CornYieldResponse {
    @JsonProperty("data")
    private List<NASSYieldData> data; 

    // Getter and Setter for 'data'
    public List<NASSYieldData> getData() {
        return data;
    }

    public void setData(List<NASSYieldData> data) {
        this.data = data;
    }
}
