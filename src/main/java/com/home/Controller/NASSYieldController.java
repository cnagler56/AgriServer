package com.home.Controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.home.Domain.NASSYieldData;
import com.home.Service.NASSYieldService;

@RestController 
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:3000")
public class NASSYieldController {


//    public NASSYieldController(NASSYieldService yieldService) {
//        this.yieldService = yieldService;
//    }
	@Autowired
	NASSYieldService yieldService;
    
    @GetMapping("/nass-yield-data")
    public ResponseEntity<List<NASSYieldData>> fetchNassYield(
    		@RequestParam String grain, 
    		@RequestParam String month, 
    		@RequestParam String year) {
        List<NASSYieldData> data = yieldService.fetchNASSYieldData(grain, month, year);
        return ResponseEntity.ok(data);
    }

    @GetMapping("/fetch-recent")
    public String fetchAndSaveMostRecent() {
        yieldService.fetchAndSaveMostRecentData();
        return "Most recent yield and acres data saved successfully.";
    }
}

