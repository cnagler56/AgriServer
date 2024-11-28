//package com.home.Controller;
//
//import java.net.http.HttpHeaders;
//import java.util.HashSet;
//import java.util.List;
//import java.util.Set;
//import java.util.stream.Collectors;
//
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.http.ResponseCookie;
//import org.springframework.http.ResponseEntity;
////import org.springframework.security.authentication.AuthenticationManager;
////import org.springframework.security.authentication.BadCredentialsException;
////import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
////import org.springframework.security.core.Authentication;
////import org.springframework.security.core.context.SecurityContextHolder;
////import org.springframework.security.core.userdetails.UserDetails;
////import org.springframework.security.crypto.password.PasswordEncoder;
//import org.springframework.web.bind.annotation.CrossOrigin;
//import org.springframework.web.bind.annotation.GetMapping;
//import org.springframework.web.bind.annotation.PostMapping;
//import org.springframework.web.bind.annotation.RequestBody;
//import org.springframework.web.bind.annotation.RequestMapping;
//import org.springframework.web.bind.annotation.RestController;
//
//import com.home.Domain.LoginDTO;
////import com.home.Domain.ERole;
//import com.home.Domain.Role;
//import com.home.Domain.User;
////import com.home.Repository.RoleRepository;
//import com.home.Repository.UserRepository;
//
//
//
////import com.home.security.AuthenticationResponse;
////import com.home.security.AuthenticationService;
////import com.home.security.UserDetailsServiceImp;
//
//import jakarta.validation.Valid;
//
//
//@RestController
//@CrossOrigin(origins = "http://localhost:3000")
//public class AuthenticationController {
//
//    private final AuthenticationService authService;
//
//    public AuthenticationController(AuthenticationService authService) {
//        this.authService = authService;
//    }
//
//
//    @PostMapping("/register")
//    public ResponseEntity<AuthenticationResponse> register(
//            @RequestBody User request
//            ) {
//        return ResponseEntity.ok(authService.register(request));
//    }
//
////    @CrossOrigin(origins = "http://localhost:3000")
////    @GetMapping("/getIn")
////    public ResponseEntity<AuthenticationResponse> getIn(@RequestBody LoginDTO request) {
//////    	 public int getIn(@RequestBody LoginDTO request) {
////       return ResponseEntity.ok(authService.authenticate(request));
//////    	return 7;
////    }
//}
