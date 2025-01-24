package com.home.Service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.home.Domain.Forecast;
import com.home.Domain.MultiWeatherResponse;
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

        if (response == null || response.getProperties() == null) {
            return List.of();
        }


        List<Weather> forecasts = response.getProperties().getPeriods().stream()
                .map(period -> {
                    Weather.Precipitation precipitation = new Weather.Precipitation(
//                            period.getPrecipitationUnitCode(),  
                            period.getPrecipitationValue()     
                        );
                	
                    Weather weather = new Weather(
                        period.getName(),
                        period.getDayForecast(),
                        period.getTemperature(),
                        precipitation,
                        period.getWindSpeed(),             
                        period.getWindDirection(),       
                        period.getShortForecast(),      
                        period.getFullForecast()       
                    );

                
                    nwsRepository.save(weather);
                    return weather;
                })
                .collect(Collectors.toList());

            return forecasts;
        }

    
    public List<MultiWeatherResponse> getForecasts(List<String> locations) {
        return locations.stream()
            .map(location -> {
                String[] parts = location.split(",");
                if (parts.length != 3) {
                    throw new IllegalArgumentException("Invalid location format. Use gridID,gridX,gridY.");
                }

                String gridID = parts[0];
                String gridX = parts[1];
                String gridY = parts[2];
                String apiUrl = String.format("https://api.weather.gov/gridpoints/%s/%s,%s/forecast", gridID, gridX, gridY);

                MultiWeatherResponse weatherResponse = new MultiWeatherResponse();
                weatherResponse.setLocation(gridID + "/" + gridX + "," + gridY);
                System.out.println(weatherResponse.getLocation() + ".........here..................");
                ForecastResponse response = restTemplate.getForObject(apiUrl, ForecastResponse.class);
                System.out.println("API Response: " + (response != null ? response.toString() : "null"));
                if (response != null && response.getProperties() != null) {
                    // Map the API periods to Forecast objects
                    List<Forecast> forecasts = response.getProperties().getPeriods().stream()
                        .map(period -> {
                            Forecast forecast = new Forecast();
                            forecast.setShortForecast(period.getShortForecast());
                            System.out.println("short forecast" + period.getShortForecast());

                            return forecast;
                        })
                        .collect(Collectors.toList());

                    weatherResponse.setForecasts(forecasts);
                }

                return weatherResponse;
            })
            .collect(Collectors.toList());
    }



}
