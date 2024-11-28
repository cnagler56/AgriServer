package com.home.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.home.Domain.NASSYieldData;
import com.home.Repository.NASSYieldDataRepository;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class NASSYieldService {
	
    @Value("${usda.api.key}")
    private String apiKey;

    	
        @Autowired
        private NASSYieldDataRepository nASSYieldDataRepository;

        @Autowired
        private RestTemplate restTemplate;

        

        public List<NASSYieldData> fetchAndSaveNASSYieldData(String grain, String month, String year) {
            // Fetch data from the external API
        	
            String url = UriComponentsBuilder.fromHttpUrl("https://quickstats.nass.usda.gov/api/api_GET/")
                    .queryParam("key", apiKey)             // API key
                    .queryParam("commodity_desc", grain)  // Commodity description
                    .queryParam("reference_period_desc", month) // Statistic category description
                    .queryParam("year", year)           // Year for the data
                    .toUriString();
        	
        	System.out.println(url);
            ResponseEntity<CornYieldResponse> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<CornYieldResponse>() {}
            );
            System.out.println(response.getBody().getData());
            if (response.getBody() != null) {
            	List<NASSYieldData> data = response.getBody().getData();
            	data.forEach(d -> {
            	    if (d.getYield() == null) {
            	        d.setYield("0");  
            	    }
            	});
//                cornYieldDataRepository.saveAll(data);

                return data;
            }

            throw new RuntimeException("Failed to fetch data from USDA API");
        }
    }

