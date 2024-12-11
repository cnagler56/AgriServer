package com.home.Controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.home.Domain.CattleData;
import com.home.Domain.HogsData;
import com.home.Repository.HogRepository;
import com.home.Service.AnimalService;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:3000")
public class AnimalController {
	
	@Autowired
	AnimalService animalService;
	
	@Autowired
	HogRepository hogRepository;
	
	
	@GetMapping("/hogs")
    public ResponseEntity<List<HogsData>> fetchHogData(
    		@RequestParam String month, 
    		@RequestParam String year) {
        List<HogsData> data = animalService.fetchHogsAndPigsReport(month, year);
        return ResponseEntity.ok(data);
    }
	
	@GetMapping("/cattle")
    public ResponseEntity<List<CattleData>> fetchCattleData(
    		@RequestParam String month, 
    		@RequestParam String year) {
        List<CattleData> data = animalService.fetchCattleOnFeedReport(month, year);
        return ResponseEntity.ok(data);
    }

}
