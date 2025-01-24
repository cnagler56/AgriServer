package com.home.Controller;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import com.home.Domain.MultiWeatherResponse;
import com.home.Domain.Weather;
import com.home.Domain.WeatherResponse;
import com.home.Service.ForecastResponse;
import com.home.Service.WeatherPersistenceService;
import com.home.Service.WeatherService;

@RestController
@CrossOrigin(origins = "http://localhost:3000")
public class WeatherController {

    private final WeatherService weatherService;
    private final WeatherPersistenceService persistenceService;
    
    private RestTemplate restTemplate;

    public WeatherController(WeatherService weatherService, WeatherPersistenceService persistenceService, RestTemplate restTemplate) {
        this.weatherService = weatherService;
        this.persistenceService = persistenceService;
        this.restTemplate = restTemplate;
    }
    

    @GetMapping("/fetch-weather")
    @CrossOrigin(origins = "http://localhost:3000")
    public List<WeatherResponse> fetchWeather(
            @RequestParam String gridID,
            @RequestParam String gridX,
            @RequestParam String gridY) {
        
        List<Weather> weatherList = weatherService.getForecast(gridID, gridX, gridY);

        return weatherList.stream()
                .map(weather -> {
                    Long precipChance = (weather.getPrecipitationValue() != null) 
                            ? weather.getPrecipitationValue() 
                            : 0L;
                    return new WeatherResponse(
                		weather.getName(),
                        weather.getDayForecast(),
                        weather.getTemperature(),
                        precipChance,
                        weather.getWindSpeed(),
                        weather.getWindDirection(),
                        weather.getShortForecast(),
                        weather.getShortForecast()
                    );
                })
                .collect(Collectors.toList());
    }
    
//    @GetMapping("/fetch-weathers")
//    public List<MultiWeatherResponse> getForecasts(@RequestParam String locations) {
//        // Split the locations parameter into individual location groups
//        String[] locationArray = locations.split(",");
//
//        if (locationArray.length % 3 != 0) {
//            throw new IllegalArgumentException("Invalid location format. Use gridID,gridX,gridY for each location.");
//        }
//
//        // Process each set of gridID, gridX, gridY
//        List<MultiWeatherResponse> weatherResponses = new ArrayList<>();
//        for (int i = 0; i < locationArray.length; i += 3) {
//            String gridID = locationArray[i];
//            String gridX = locationArray[i + 1];
//            String gridY = locationArray[i + 2];
//
//            // Construct the API URL
//            String apiUrl = String.format("https://api.weather.gov/gridpoints/%s/%s,%s/forecast", gridID, gridX, gridY);
//
//            // Initialize a MultiWeatherResponse object
//            MultiWeatherResponse weatherResponse = new MultiWeatherResponse();
//            weatherResponse.setLocation(gridID + "/" + gridX + "," + gridY);
//
//            // Fetch the weather data
//            ForecastResponse forecastResponse = restTemplate.getForObject(apiUrl, ForecastResponse.class);
//            if (forecastResponse != null && forecastResponse.getProperties() != null
//                    && forecastResponse.getProperties().getPeriods() != null) {
//                weatherResponse.setForecasts(forecastResponse.getProperties().getPeriods().stream()
//                    .map(period -> {
//                        Forecast forecast = new Forecast();
//                        forecast.setShortForecast(period.getShortForecast());
////                        forecast.setStartTime(period.getStartTime());
////                        forecast.setTemperature(period.getTemperature());
//                        return forecast;
//                    })
//                    .collect(Collectors.toList()));
//            } else {
//                System.out.println("No data returned for location: " + gridID + "/" + gridX + "," + gridY);
//                weatherResponse.setForecasts(Collections.emptyList()); // Set empty forecasts for this location
//            }
//
//            // Add to the list of responses
//            weatherResponses.add(weatherResponse);
//        }
//
//        // Return the complete list of responses
//        return weatherResponses;
//    }
//


}

