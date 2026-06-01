package com.home.Controller;

import java.util.UUID;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.home.Domain.LoginDTO;
import com.home.Domain.Post;
import com.home.Domain.User;
import com.home.Service.PostService;
import com.home.Service.UserService;

/**
 * Legacy entry-point controller — handles users, posts, and auth.
 *
 * Crop/yield endpoints (cornestimates, beanestimates, cornGuess, beanGuess,
 * cornyields, beans) were removed when the corn + soybean estimators were
 * unified into /usda-reports and /api/yield-guess.
 */
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

	@GetMapping("/post/{idposts}")
	public Post posta(@RequestParam Long idposts) {
		return this.postService.getfullPost(idposts);
	}

	@GetMapping("/getIn")
	public UUID get(@RequestBody LoginDTO use) {
		return this.userService.getIn(use);
	}

	@GetMapping("/login")
	public User login(@RequestParam String email, @RequestParam String password) {
		return this.userService.login(email, password);
	}

	@PostMapping("/register")
	public User register(@RequestBody User user) {
		return this.userService.register(user);
	}

	@PostMapping("/addpost")
	public void addPost(@RequestBody Post post) {
		this.postService.addPost(post);
	}
}
