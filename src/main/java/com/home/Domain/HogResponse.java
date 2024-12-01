package com.home.Domain;


import java.util.List;

public class HogResponse {
    private List<HogsData> data; // Maps the data field in the API response

    public List<HogsData> getData() {
        return data;
    }

    public void setData(List<HogsData> data) {
        this.data = data;
    }

    @Override
    public String toString() {
        return "HogResponse{" +
                "data=" + data +
                '}';
    }
}
