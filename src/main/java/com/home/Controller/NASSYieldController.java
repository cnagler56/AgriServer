package com.home.Controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


import com.home.Domain.NASSYieldData;
import com.home.Service.NASSYieldService;

@RestController
@RequestMapping("/nass-yield-data")
@CrossOrigin(origins = "http://localhost:3000")
public class NASSYieldController {

    @Autowired
    private NASSYieldService nASSYieldService;

    @GetMapping
    public ResponseEntity<List<NASSYieldData>> fetchNassYield(String grain, String month, String year) {
        List<NASSYieldData> data = nASSYieldService.fetchAndSaveNASSYieldData(grain, month, year);
        return ResponseEntity.ok(data);
    }
}

