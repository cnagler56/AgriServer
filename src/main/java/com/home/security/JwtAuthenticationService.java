package com.home.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.home.Service.JwtTokenProvider;

@Service
public class JwtAuthenticationService {

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    // This method can be used to extract the username (or user identifier) from the token
    public String getUserFromToken(String token) {
        if (jwtTokenProvider.validateToken(token)) {
            return jwtTokenProvider.getUsernameFromToken(token);
        }
        return null;
    }
}

