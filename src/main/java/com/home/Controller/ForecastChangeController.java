package com.home.Controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.home.Domain.ForecastLocation;
import com.home.Service.ForecastChangeService;

@RestController
@RequestMapping("/api/forecast-locations")
@CrossOrigin(origins = "http://localhost:3000")
public class ForecastChangeController {

	private final ForecastChangeService service;

	public ForecastChangeController(ForecastChangeService service) {
		this.service = service;
	}

	@GetMapping
	public List<ForecastLocation> list() {
		return service.list();
	}

	@PostMapping
	public ForecastLocation create(@RequestBody ForecastLocation body) {
		return service.create(body);
	}

	@PutMapping("/{id}")
	public ForecastLocation update(@PathVariable Long id, @RequestBody ForecastLocation body) {
		return service.update(id, body);
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable Long id) {
		service.delete(id);
		return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
	}

	@PostMapping("/{id}/refresh")
	public ForecastLocation refresh(@PathVariable Long id) {
		return service.refresh(id);
	}
}
