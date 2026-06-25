package com.home.Controller;

import java.util.Map;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.home.Service.CropProductionService;

/**
 * /api/crop-production/{commodity} — county-level production (bushels) keyed by
 * 5-digit FIPS, for the forecast-map production overlay. Read-only, open to all.
 */
@RestController
@RequestMapping("/api/crop-production")
@CrossOrigin(origins = "http://localhost:3000", allowCredentials = "true")
public class CropProductionController {

    private final CropProductionService service;

    public CropProductionController(CropProductionService service) {
        this.service = service;
    }

    @GetMapping("/{commodity}")
    public Map<String, Object> production(@PathVariable String commodity) {
        return service.getProduction(commodity);
    }
}
