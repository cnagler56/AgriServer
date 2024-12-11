package com.home.Controller;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


import com.home.Domain.Weather;
import com.home.Domain.WeatherResponse;
import com.home.Service.WeatherPersistenceService;
import com.home.Service.WeatherService;

@RestController
@CrossOrigin(origins = "http://localhost:3000")
public class WeatherController {

    private final WeatherService weatherService;
    private final WeatherPersistenceService persistenceService;

    public WeatherController(WeatherService weatherService, WeatherPersistenceService persistenceService) {
        this.weatherService = weatherService;
        this.persistenceService = persistenceService;
    }
    

    @GetMapping("/fetch-weather")
    public List<WeatherResponse> fetchWeather(
            @RequestParam String gridID,
            @RequestParam String gridX,
            @RequestParam String gridY) {
        
        List<Weather> weatherList = weatherService.getForecast(gridID, gridX, gridY);

        return weatherList.stream()
                .map(weather -> {
                    Double precipChance = (weather.getPrecipitation() != null && weather.getPrecipitation().getValue() != null)
                            ? weather.getPrecipitation().getValue() // Use the value if it exists
                            : 0.0;
                    return new WeatherResponse(
                		weather.getName(),
                        weather.getDayForecast(),
                        weather.getTemperature(),
                        precipChance,
                        weather.getWindSpeed(),
                        weather.getWindDirection(),
                        weather.getShortForecast(),
                        weather.getFullForecast()
                    );
                })
                .collect(Collectors.toList());
    }
}



