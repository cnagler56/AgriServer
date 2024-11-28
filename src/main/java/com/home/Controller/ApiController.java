package com.home.Controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;

import com.home.Domain.User;
import com.home.Repository.UserRepository;
import com.home.Service.UserService;

@RestController
@CrossOrigin(origins = "http://localhost:3000")
public class ApiController {
	
	
	private final UserRepository userRepository;
	
	public ApiController(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

    private final RestTemplate restTemplate = new RestTemplate();


//    @GetMapping("/usda")
//    @CrossOrigin(origins = "http://localhost:3000")
//    public List<User> getDataFromExternalApi() {
//    	return this.userRepository.findAll();
//        
////        return result; // Returning the JSON data as-is to the React app
//    }
}
