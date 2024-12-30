package com.home.Controller;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.security.auth.login.AccountNotFoundException;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.home.Domain.AuthResponse;
import com.home.Domain.BeanGuess;
import com.home.Domain.Beans;
import com.home.Domain.CornGuess;
import com.home.Domain.CornYields;
import com.home.Domain.LoginDTO;
import com.home.Domain.Post;
import com.home.Domain.Role;
import com.home.Domain.User;
import com.home.Domain.DTO.LoginRequest;
import com.home.Domain.DTO.RegistrationRequest;
import com.home.Repository.UserRepository;
import com.home.Service.GrainService;
import com.home.Service.JwtTokenProvider;
import com.home.Service.PostService;
import com.home.Service.UserService;
import com.home.security.CustomUserDetails;
import com.home.security.JwtAuthenticationResponse;

@RestController
@CrossOrigin(origins = "http://localhost:3000")
public class IndexController {
	
	private final UserService userService;
	private final PostService postService;
	private final GrainService grainService;
	private final JwtTokenProvider jwtTokenProvider;
	private final UserRepository userRepository;
	private final AuthenticationManager authenticationManager;

	
	public IndexController(UserService userService, PostService postService, GrainService grainService,
			JwtTokenProvider jwtTokenProvider, UserRepository userRepository, AuthenticationManager authenticationManager) {
		this.userService = userService;
		this.postService = postService;
		this.grainService = grainService;
		this.jwtTokenProvider = jwtTokenProvider;
		this.userRepository = userRepository;
		this.authenticationManager = authenticationManager;
	 
		 
	}
    @Autowired
    private PasswordEncoder passwordEncoder;

	
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
	
	@GetMapping("/cornestimates")
	public List <CornGuess> cornGuess() {
		return this.grainService.getCornGuess();
	}

    
	@GetMapping("/beans")
	   public Iterable<Beans> beans() {
		return this.grainService.getBeans();
}
	
	@GetMapping("/cornyields")
	   public Iterable<CornYields> getCorn() {
		return this.grainService.getCorn();
}

	@PostMapping("/login")
	public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest) {
	      
	    Authentication authentication = authenticationManager.authenticate(
	        new UsernamePasswordAuthenticationToken(loginRequest.getEmail(), loginRequest.getPassword())
	    );
	    
	    // Generate JWT token
	    String token = jwtTokenProvider.createToken(authentication.getName());
	    
	     
	    return ResponseEntity.ok(new JwtAuthenticationResponse(token));
	}


    @PostMapping("/register")
    public ResponseEntity<String> registerUser(@RequestBody RegistrationRequest request) {
     
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Email already exists!");
        }

        
        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Username already exists!");
        }

      
        User newUser = new User();
        newUser.setFirstName(request.getFirstName());
        newUser.setLastName(request.getLastName());
        newUser.setUsername(request.getUsername());
        newUser.setEmail(request.getEmail());
        newUser.setCity(request.getCity());
        newUser.setState(request.getState());
        newUser.setPassword(passwordEncoder.encode(request.getPassword())); // Encode password
        newUser.setInterest(request.getInterest());
        newUser.setActive(true); // Mark user as active by default
        newUser.setRoles(Set.of(Role.USER)); // Assign default role

        userRepository.save(newUser);

        return ResponseEntity.status(HttpStatus.CREATED).body("User registered successfully!");
    }
	
	   @PostMapping("/cornGuess")
	    public void recordGuess (@RequestBody CornGuess cornGuess) {
	      this.grainService.addCornYield(cornGuess);
	    }
	   
	   @PostMapping("/beanGuess")
	   public void beanguess(@RequestBody BeanGuess beanGuess) {
		   this.grainService.addBeanGuess(beanGuess);
	   }
	
	@PostMapping("/addpost")
	public void addPost(@RequestBody Post post) {
		this.postService.addPost(post);
	}
	
}
