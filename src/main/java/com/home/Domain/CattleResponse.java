package com.home.Domain;

import java.util.List;

public class CattleResponse {
    private List<CattleData> data; // Maps the data field in the API response

    public List<CattleData> getData() {
        return data;
    }

    public void setData(List<CattleData> data) {
        this.data = data;
    }

    @Override
    public String toString() {
        return "CattleResponse{" +
                "data=" + data +
                '}';
    }
}
