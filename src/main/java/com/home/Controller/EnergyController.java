package com.home.Controller;

import java.util.Map;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.home.Service.EnergyService;

/**
 * /api/energy — weekly petroleum (crude, diesel demand/price, propane).
 * /api/energy/soyoil-biofuel — soybean oil consumed by biodiesel & renewable diesel.
 * Read-only, open to all.
 */
@RestController
@RequestMapping("/api/energy")
@CrossOrigin(origins = "http://localhost:3000", allowCredentials = "true")
public class EnergyController {

    private final EnergyService service;

    public EnergyController(EnergyService service) {
        this.service = service;
    }

    @GetMapping
    public Map<String, Object> energy() {
        return service.getEnergy();
    }

    @GetMapping("/soyoil-biofuel")
    public Map<String, Object> soyOilBiofuel() {
        return service.getSoyOilBiofuel();
    }
}
