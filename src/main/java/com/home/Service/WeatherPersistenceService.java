package com.home.Service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.home.Domain.Weather;
import com.home.Repository.NWSRepository;

@Service
public class WeatherPersistenceService {

    private final NWSRepository weatherRepository;

    public WeatherPersistenceService(NWSRepository weatherRepository) {
        this.weatherRepository = weatherRepository;
    }

    public void saveForecast(List<Weather> forecast) {
        weatherRepository.saveAll(forecast);
    }
}
