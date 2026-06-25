package com.home.Controller;

import java.util.Map;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.home.Service.GrainStocksService;

/** /api/grain-stocks/{commodity} — NASS quarterly Grain Stocks. Read-only, open. */
@RestController
@RequestMapping("/api/grain-stocks")
@CrossOrigin(origins = "http://localhost:3000", allowCredentials = "true")
public class GrainStocksController {

    private final GrainStocksService service;

    public GrainStocksController(GrainStocksService service) {
        this.service = service;
    }

    @GetMapping("/{commodity}")
    public Map<String, Object> stocks(@PathVariable String commodity) {
        return service.getStocks(commodity);
    }
}
