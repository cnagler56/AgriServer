package com.home.Controller;

import java.util.Map;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.home.Service.ExportSalesService;

/** /api/export-sales/{commodity} — USDA FAS weekly export sales. Read-only, open. */
@RestController
@RequestMapping("/api/export-sales")
@CrossOrigin(origins = "http://localhost:3000", allowCredentials = "true")
public class ExportSalesController {

    private final ExportSalesService service;

    public ExportSalesController(ExportSalesService service) {
        this.service = service;
    }

    @GetMapping("/{commodity}")
    public Map<String, Object> exportSales(@PathVariable String commodity) {
        return service.getExportSales(commodity);
    }
}
