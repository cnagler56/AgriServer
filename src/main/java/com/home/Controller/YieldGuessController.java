package com.home.Controller;

import java.util.List;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.home.Domain.YieldGuess;
import com.home.Repository.YieldGuessRepository;

@RestController
@RequestMapping("/api/yield-guess")
@CrossOrigin(origins = "http://localhost:3000", allowCredentials = "true")
public class YieldGuessController {

	private final YieldGuessRepository repo;

	public YieldGuessController(YieldGuessRepository repo) { this.repo = repo; }

	@GetMapping("/{commodity}")
	public List<YieldGuess> list(@PathVariable String commodity) {
		return repo.findByCommodityOrderByDateDesc(commodity.toUpperCase());
	}

	@PostMapping
	public YieldGuess submit(@RequestBody YieldGuess guess) {
		guess.setId(null);
		if (guess.getCommodity() != null) guess.setCommodity(guess.getCommodity().toUpperCase());
		return repo.save(guess);
	}
}
