package com.home.Service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.home.Domain.Weather;
import com.home.Domain.WeatherResponse;
import com.home.Repository.NWSRepository;

@Service
public class WeatherService {


    
    private final RestTemplate restTemplate;
    private final NWSRepository nwsRepository;
    
    public WeatherService(RestTemplate restTemplate, NWSRepository nwsRepository) {
        this.restTemplate = restTemplate;
        this.nwsRepository = nwsRepository;
    }
	
    
    public List<Weather> getForecast(String gridID, String gridX, String gridY) {
        String apiUrl = String.format("https://api.weather.gov/gridpoints/%s/%s,%s/forecast", gridID, gridX, gridY);
        ForecastResponse response = restTemplate.getForObject(apiUrl, ForecastResponse.class);
        return response != null && response.getProperties() != null 
               ? response.getProperties().getPeriods() 
               : List.of();
   
}
}
