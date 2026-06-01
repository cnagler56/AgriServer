package com.home.Controller;

import java.util.List;
import java.util.Optional;

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

import com.home.Domain.Field;
import com.home.Repository.FieldRepository;

/**
 * CRUD for the user's field/farm records.
 * No auth layer in this app — userId is sent in the path or body by the client.
 */
@RestController
@RequestMapping("/api/fields")
@CrossOrigin(origins = "http://localhost:3000")
public class FieldController {

	private final FieldRepository fieldRepository;

	public FieldController(FieldRepository fieldRepository) {
		this.fieldRepository = fieldRepository;
	}

	@GetMapping("/{userId}")
	public List<Field> listForUser(@PathVariable Long userId) {
		return fieldRepository.findByUserIdOrderByCreatedAtDesc(userId);
	}

	@PostMapping
	public Field create(@RequestBody Field field) {
		field.setId(null);  // ignore any client-supplied id
		return fieldRepository.save(field);
	}

	@PutMapping("/{id}")
	public ResponseEntity<Field> update(@PathVariable Long id, @RequestBody Field incoming) {
		Optional<Field> existing = fieldRepository.findById(id);
		if (existing.isEmpty()) return ResponseEntity.notFound().build();
		Field f = existing.get();
		// Only allow editing of business fields — userId and createdAt are not changed
		f.setName(incoming.getName());
		f.setAcres(incoming.getAcres());
		f.setCrop(incoming.getCrop());
		f.setVariety(incoming.getVariety());
		f.setPlantedOn(incoming.getPlantedOn());
		f.setLat(incoming.getLat());
		f.setLon(incoming.getLon());
		f.setNotes(incoming.getNotes());
		return ResponseEntity.ok(fieldRepository.save(f));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable Long id) {
		if (!fieldRepository.existsById(id)) return ResponseEntity.notFound().build();
		fieldRepository.deleteById(id);
		return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
	}
}
