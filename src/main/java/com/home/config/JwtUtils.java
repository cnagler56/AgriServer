//package com.home.config;
//
//import java.security.Key;
//
//import java.util.Date;
//
//import jakarta.servlet.http.Cookie;
//import jakarta.servlet.http.HttpServletRequest;
//
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.http.ResponseCookie;
//import org.springframework.stereotype.Component;
//import org.springframework.web.util.WebUtils;
//
//import com.home.security.UserDetailsServiceImp;
//
//import io.jsonwebtoken.*;
//import io.jsonwebtoken.io.Decoders;
//import io.jsonwebtoken.security.Keys;
//
//
//@Component
//public class JwtUtils {
//	
//	
//  private static final Logger logger = LoggerFactory.getLogger(JwtUtils.class);
//
//  @Value("${jwt.secret}")
//  private String jwtSecret;
//  
//  public String extractEmail(String token) {
//	  return extractClaim(token, Claims::getSubject);
//  }
//
////  public boolean isValid(String token, UserDetails user) {
////	  String email = extractEmail(token);
////	  
////	  boolean validToken = 
////  }
//  
//  private boolean isTokenExpired(String Token) {
//	  
//  }
//
//
//
//
//  public String generateTokenFromEmail(userDetails) {   
//    return Jwts.builder()
//               .setSubject(email)
//               .setIssuedAt(new Date())
//               .setExpiration(new Date((new Date()).getTime() + jwtExpirationMs))
//               .signWith(key(), SignatureAlgorithm.HS256)
//               .compact();
//  }
//}