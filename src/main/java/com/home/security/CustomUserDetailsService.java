package com.home.security;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.context.annotation.Bean;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.home.Domain.MyUsers;
import com.home.Repository.UserRepository;


@Service
public class CustomUserDetailsService implements UserDetailsService {

	@Autowired
	private UserRepository userRepository;


    
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
    	System.out.println("Fetching user by email: " + email);
    	Optional <MyUsers> user = userRepository.findByEmail(email);
           if(user.isPresent()) {
        	   var userObj = user.get();
        	   return User.builder()
        			   .username(userObj.getEmail())
        			   .password(userObj.getPassword())
        		         .roles(
        		                    userObj.getRoles().stream()
        		                        .map(role -> "ROLE_" + role.name()) // Map enums to role names
        		                        .toArray(String[]::new) // Convert to String array
        		                )
        			   .build();
           } else {
        	   throw new UsernameNotFoundException(email);
           }

    }
    

}




