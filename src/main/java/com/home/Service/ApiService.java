package com.home.Service;

import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
public class ApiService {
	



    private final WebClient webClient;

    public ApiService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.baseUrl("https://espn.com").build();
    }

    public String getApiResponse(String endpoint) {
        Mono<String> response = webClient.get()
                                         .uri(endpoint)
                                         .retrieve()
                                         .bodyToMono(String.class);
        System.out.println(response);
        return response.block(); // Blocking for demonstration; ideally, use reactive handling
    }
}