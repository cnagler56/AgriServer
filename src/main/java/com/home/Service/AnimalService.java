package com.home.Service;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.home.Domain.HogResponse;
import com.home.Domain.HogsData;
import com.home.Domain.NASSYieldData;
import com.home.Repository.HogRepository;
import com.home.Repository.NASSYieldDataRepository;

@Service
public class AnimalService {

    @Value("${usda.api.key}")
    private String apiKey;
    
    private final RestTemplate restTemplate;
    private final HogRepository hogRepository;
    
    public AnimalService(RestTemplate restTemplate, HogRepository hogRepository) {
        this.restTemplate = restTemplate;
        this.hogRepository = hogRepository;
    }
	
    public List<HogsData> fetchHogsAndPigsReport(String month, String year) {
        // Fetch data from the external API
        
        String url = UriComponentsBuilder.fromHttpUrl("https://quickstats.nass.usda.gov/api/api_GET/")
                .queryParam("key", apiKey)             
                .queryParam("sector", "ANIMALS+%26+PRODUCTS")   
                .queryParam("group", "HOGS")
                .queryParam("commodity_desc", "HOGS")
                .queryParam("statisticcat_desc", "INVENTORY")
                .queryParam("month", month)
                .queryParam("year", year)          
                .toUriString();
        
        ResponseEntity<HogResponse> response = restTemplate.exchange(
            url,
            HttpMethod.GET,
            null,
            new ParameterizedTypeReference<HogResponse>() {}
        );
        System.out.println(response.getBody().getData());
        if (response.getBody() != null) {
            List<HogsData> data = response.getBody().getData();
            data.forEach(d -> {

            });


            return data;
        }

        throw new RuntimeException("Failed to fetch data from USDA API");
    }
}
	

