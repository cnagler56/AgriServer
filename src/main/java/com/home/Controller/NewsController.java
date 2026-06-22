package com.home.Controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.home.Service.NewsService;

/**
 * /api/news — auto-generated "Latest News" feed (last 3 days), built from the
 * WASDE / ethanol / ENSO / price data we already cache. Read-only, open to all.
 */
@RestController
@RequestMapping("/api/news")
@CrossOrigin(origins = "http://localhost:3000", allowCredentials = "true")
public class NewsController {

	private final NewsService service;

	public NewsController(NewsService service) {
		this.service = service;
	}

	@GetMapping
	public List<Map<String, Object>> news() {
		return service.getNews();
	}
}
