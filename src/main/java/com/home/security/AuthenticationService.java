//package com.home.security;
//
//import java.util.List;
//
//import org.apache.commons.logging.Log;
//import org.springframework.security.authentication.AuthenticationManager;
//import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
//import org.springframework.security.crypto.password.PasswordEncoder;
//import org.springframework.stereotype.Service;
//
//import com.home.Domain.LoginDTO;
//import com.home.Domain.Token;
//import com.home.Domain.User;
//import com.home.Repository.TokenRepository;
//import com.home.Repository.UserRepository;
//import com.home.config.JwtService;
//
//@Service
//public class AuthenticationService {
//
//    private final UserRepository repository;
//    private final PasswordEncoder passwordEncoder;
//    private final JwtService jwtService;
//
//    private final TokenRepository tokenRepository;
//
//    private final AuthenticationManager authenticationManager;
//
//    public AuthenticationService(UserRepository repository,
//                                 PasswordEncoder passwordEncoder,
//                                 JwtService jwtService,
//                                 TokenRepository tokenRepository,
//                                 AuthenticationManager authenticationManager) {
//        this.repository = repository;
//        this.passwordEncoder = passwordEncoder;
//        this.jwtService = jwtService;
//        this.tokenRepository = tokenRepository;
//        this.authenticationManager = authenticationManager;
//    }
//
//    public AuthenticationResponse register(User request) {
//
//        // check if user already exist. if exist than authenticate the user
//        if(repository.findByUsername(request.getUsername()).isPresent()) {
//            return new AuthenticationResponse(null, "User already exist");
//        }
//
//        User user = new User();
//        user.setFirstName(request.getFirstName());
//        user.setLastName(request.getLastName());
//        user.setUsername(request.getUsername());
//        user.setPassword(passwordEncoder.encode(request.getPassword()));
////        user.setUserId((long) 600);
//        user.setCity(request.getCity());
//        user.setState(request.getState());
//        user.setRole(request.getRoles());
//        user.setEmail(request.getEmail());
//        user.setInterest(request.getInterest());
//        user.setActive(request.getActive());
//        user.setName(request.getName());
//
//        user = repository.save(user);
//
//        String jwt = jwtService.generateToken(user);
//
//        saveUserToken(jwt, user);
//
//        return new AuthenticationResponse(jwt, "User registration was successful");
//
//    }
//
//    public AuthenticationResponse authenticate(LoginDTO request) {
//    	System.out.println(request.getPassword());
//    	System.out.println(request.getUsername());
//        authenticationManager.authenticate(
//                new UsernamePasswordAuthenticationToken(
//                		
//                        request.getUsername(),                     
//                        request.getPassword()
//                )
//        );
//        
//        User user = repository.findByUsername(request.getUsername()).orElseThrow();
//        String jwt = jwtService.generateToken(user);
//
//        revokeAllTokenByUser(user);
//        saveUserToken(jwt, user);
//
//        return new AuthenticationResponse(jwt, "User login was successful");
//
//    }
//    private void revokeAllTokenByUser(User user) {
//        List<Token> validTokens = tokenRepository.findAllTokensByUser(user.getUserId());
//        if(validTokens.isEmpty()) {
//            return;
//        }
//
//        validTokens.forEach(t-> {
//            t.setLoggedOut(true);
//        });
//
//        tokenRepository.saveAll(validTokens);
//    }
//    private void saveUserToken(String jwt, User user) {
//        Token token = new Token();
//        token.setToken(jwt);
//        token.setLoggedOut(false);
//        token.setUser(user);
//        tokenRepository.save(token);
//    }
//}
