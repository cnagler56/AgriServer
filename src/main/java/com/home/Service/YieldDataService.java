//package com.home.Service;
//
//import java.time.LocalDateTime;
//import java.util.Arrays;
//
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.stereotype.Service;
//import org.springframework.web.client.RestTemplate;
//
//import com.home.Domain.NASSYieldData;
//import com.home.Repository.NASSYieldDataRepository;
//
//@Service
//public class YieldDataService {
//
//    private static final String API_KEY = "F8384D57-022C-34E7-8C3E-93CC8622D2F5";
//    private static final String NASS_API_URL = "https://quickstats.nass.usda.gov/api/api_GET/";
//
//    @Autowired
//    private RestTemplate restTemplate;
//
//    @Autowired
//    private NASSYieldDataRepository yieldDataRepository;
//
//    public void fetchAndStoreYieldData() {
//        String[] commodities = {"CORN", "SOYBEANS", "WHEAT"};
//
//        Arrays.stream(commodities).forEach(commodity -> {
//            String url = String.format(
//                "%s?key=%s&commodity_desc=%s&statisticcat_desc=YIELD&year=%d",
//                NASS_API_URL, API_KEY, commodity, LocalDateTime.now().getYear()
//            );
//
//            try {
//                CornYieldResponse response = restTemplate.getForObject(url, CornYieldResponse.class);
//                if (response != null && response.getData() != null) {
//                    response.getData().forEach(item -> {
//                        NASSYieldData yieldData = new NASSYieldData();
//                        yieldData.setGrain(item.getGrain());
//                        yieldData.setStateName(item.getStateName());
//                        yieldData.setYield(item.getYield());
//                        yieldData.setLoadTime(item.getLoadTime());
//                        yieldDataRepository.save(yieldData);
//                    });
//                }
//            } catch (Exception e) {
//                e.printStackTrace(); // Handle error appropriately
//            }
//        });
//    }
//}