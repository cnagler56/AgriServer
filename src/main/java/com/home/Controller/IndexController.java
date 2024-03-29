package com.home.Controller;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.home.Domain.Beans;
import com.home.Domain.CornYields;
import com.home.Domain.Post;
import com.home.Domain.User;
import com.home.Service.GrainService;
import com.home.Service.PostService;
import com.home.Service.UserService;

@RestController
@CrossOrigin(origins = "http://localhost:3000")
public class IndexController {
	
	private final UserService userService;
	private final PostService postService;
	private final GrainService grainService;
	
	public IndexController(UserService userService, PostService postService, GrainService grainService) {
		this.userService = userService;
		this.postService = postService;
		this.grainService = grainService;
	}
	
	@GetMapping("/user")
	   public Iterable<User> users() {
        return this.userService.getList();
    }
	
	@GetMapping("/posts")
	   public Iterable<Post> post() {
     return this.postService.getPosts();
     
 }
	
//    @GetMapping("/filteredPosts") 
//    public Iterable<Post> getCertain (String title, String state){
//   	 return this.postService.getFiltered(title, state);
//    }
    
	@GetMapping("/beans")
	   public Iterable<Beans> beans() {
		return this.grainService.getBeans();
}
	
	@GetMapping("/cornyields")
	   public Iterable<CornYields> getCorn() {
		return this.grainService.getCorn();
}
	
	@PostMapping("/register")
	public void register(@RequestBody User user) {
		this.userService.saveUser(user);
	}
	
	
	@PostMapping("/addpost")
	public void addPost(@RequestBody Post post) {
		this.postService.addPost(post);
	}
	
}
