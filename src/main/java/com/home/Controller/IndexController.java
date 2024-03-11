package com.home.Controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.home.Domain.User;
import com.home.Service.UserService;

@RestController
public class IndexController {
	
	private final UserService userService;
	
	public IndexController(UserService userService) {
		this.userService = userService;
	}
	
	@GetMapping("/user")
	   public Iterable<User> users() {
        return this.userService.getList();
    }
	
	
//	@PostMapping("/postUsers")
	
}
