package com.home.Service;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.home.Domain.CombinedYieldData;
import com.home.Domain.NASSYieldData;
import com.home.Repository.NASSYieldDataRepository;
import com.home.Service.ApiResponse.ApiItem;


@Service
public class NASSYieldService {
	
    @Value("${usda.api.key}")
    private String apiKey;

//    private static final String YIELD_API_URL = "https://quickstats.nass.usda.gov/api/api_GET/?key={apiKey}&commodity_desc=CORN&&year=2020&statisticcat_desc=YIELD";
//    private static final String ACRES_API_URL = "https://quickstats.nass.usda.gov/api/api_GET/?key={apiKey}&commodity_desc=CORN&statisticcat_desc=ACRES";
//    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private final RestTemplate restTemplate;
    private final NASSYieldDataRepository yieldDataRepository;

    public NASSYieldService(RestTemplate restTemplate, NASSYieldDataRepository yieldDataRepository) {
        this.restTemplate = restTemplate;
        this.yieldDataRepository = yieldDataRepository;
    }


//        private static final String API_URL = "https://quickstats.nass.usda.gov/api/api_GET/?key={apiKey}";
        

        /**
         * Fetch data from NASS API with dynamic parameters.
         *
         * @param commodity   e.g., "CORN", "SOYBEANS", "WHEAT"
         * @param year        e.g., 2023
         * @param month       e.g., "AUG"
         * @param statistic   e.g., "YIELD" or "ACRES"
         * @return ApiResponse containing fetched data
         */
        public ApiResponse fetchDataWithParameters(String commodity, String month, String year, String statistic) {
        	fetchAndSaveMostRecentData();
        	String url = UriComponentsBuilder.fromHttpUrl("https://quickstats.nass.usda.gov/api/api_GET/")
        		    .queryParam("key", apiKey)
        		    .queryParam("commodity_desc", commodity)
        		    .queryParam("year", year)
//        		    .queryParam("reference_period_desc", month)
        		    .queryParam("statisticcat_desc", statistic)
        		    .toUriString();
        	
            Map<String, String> params = new HashMap<>();
            params.put("commodity", commodity);
            params.put("year", String.valueOf(year));
            params.put("month", month);
            params.put("statistic", statistic);

            return restTemplate.getForObject(url, ApiResponse.class, params);
        }

        /**
         * Fetch yield and acres data, consolidate, and save to database.
         */
//        public void fetchAndSaveMostRecentData() {
//            String[] commodities = {"CORN", "SOYBEANS", "WHEAT"};
//            String[] statistics = {"YIELD", "ACRES"};
//
//            for (String commodity : commodities) {
//                // Fetch YIELD data
//                ApiResponse yieldResponse = fetchDataWithParameters(commodity, "2024",  "NOV", "YIELD");
//
//                // Fetch ACRES data
//                ApiResponse acresResponse = fetchDataWithParameters(commodity, "2024", "NOV", "ACRES");
//
//                if (yieldResponse != null && acresResponse != null) {
//                    consolidateAndSave(yieldResponse, acresResponse);
//                }
//            }
//        }
        
        public void fetchAndSaveMostRecentData() {
            String[] commodities = {"CORN", "SOYBEANS", "WHEAT"};
            String statistics = "YIELD";

            for (String commodity : commodities) {
                // Fetch YIELD data
                ApiResponse yieldResponse = fetchDataWithParameters(commodity, "2024",  "NOV", "YIELD");
                
                if (yieldResponse.getData() != null) {
                    for (ApiItem yieldItem : yieldResponse.getData()) {
                        NASSYieldData entity = new NASSYieldData();
                        entity.setCommodity(yieldItem.getCommodity());
                        entity.setState(yieldItem.getState());
                        entity.setYield(yieldItem.getYield());
                        entity.setLoadTime(yieldItem.getLoad_time());

                        yieldDataRepository.save(entity); 

                    }}
            
        }
        }
        public List<NASSYieldData> fetchNASSYieldData (String grain, String month, String year){
            ApiResponse yieldResponse = fetchDataWithParameters(grain, month, year, "YIELD");
            ApiResponse acresResponse = fetchDataWithParameters(grain, month, year, "YIELD");
             
                return consolidate(yieldResponse, acresResponse);
        }
        

        /**
         * Consolidates yield and acres data and saves to the database.
         *
         * @param yieldResponse ApiResponse for yield
         * @param acresResponse ApiResponse for acres
         */
        private void consolidateAndSave(ApiResponse yieldResponse, ApiResponse acresResponse) {
            Map<String, String> acresMap = new HashMap<>();
            if (acresResponse.getData() != null) {
                for (ApiItem acresItem : acresResponse.getData()) {
                    acresMap.put(acresItem.getState(), acresItem.getYield());
                }
            }


            if (yieldResponse.getData() != null) {
                for (ApiItem yieldItem : yieldResponse.getData()) {
                    NASSYieldData entity = new NASSYieldData();
                    entity.setCommodity(yieldItem.getCommodity());
                    entity.setState(yieldItem.getState());
                    entity.setYield(yieldItem.getYield());
                    entity.setAcresValue(acresMap.getOrDefault(yieldItem.getState(), "N/A")); // Match state for acres
                    entity.setLoadTime(yieldItem.getLoad_time());

                    yieldDataRepository.save(entity); // Save to the database
                }
            }      
        }
        
        private List<NASSYieldData> consolidate(ApiResponse yieldResponse, ApiResponse acresResponse) {
            Map<String, String> acresMap = new HashMap<>();

            // Map state to acres from acresResponse
            if (acresResponse != null && acresResponse.getData() != null) {
                for (ApiResponse.ApiItem acresItem : acresResponse.getData()) {
                    acresMap.put(acresItem.getState(), acresItem.getYield()); // Assuming "getYield" for acres value
                }
            }

            List<NASSYieldData> consolidatedData = new ArrayList<>();

            // Consolidate yield data with acres data
            if (yieldResponse != null && yieldResponse.getData() != null) {
                for (ApiResponse.ApiItem yieldItem : yieldResponse.getData()) {
                    NASSYieldData entity = new NASSYieldData();
                    entity.setCommodity(yieldItem.getCommodity());
                    entity.setState(yieldItem.getState());
                    entity.setYield(yieldItem.getYield());
                    entity.setAcresValue(acresMap.getOrDefault(yieldItem.getState(), "N/A")); // Match state for acres
                    entity.setLoadTime(yieldItem.getLoad_time());

                    consolidatedData.add(entity); 
                }
            }

            return consolidatedData; 
        }

    }
    


