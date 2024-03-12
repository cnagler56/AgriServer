package com.home.Controller;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.home.Domain.Post;
import com.home.Domain.User;
import com.home.Service.PostService;
import com.home.Service.UserService;

@RestController
@CrossOrigin(origins = "http://localhost:3000")
public class IndexController {
	
	private final UserService userService;
	private final PostService postService;
	
	public IndexController(UserService userService, PostService postService) {
		this.userService = userService;
		this.postService = postService;
	}
	
	@GetMapping("/user")
	   public Iterable<User> users() {
        return this.userService.getList();
    }
	
	@GetMapping("/posts")
	   public Iterable<Post> post() {
     return this.postService.getPosts();
 }
	
	
//	@PostMapping("/postUsers")
	
}
