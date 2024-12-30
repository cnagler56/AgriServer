//package com.home.security;
//
//import io.jsonwebtoken.Claims;
//import io.jsonwebtoken.Jwts;
//import io.jsonwebtoken.SignatureAlgorithm;
//import org.springframework.stereotype.Component;
//import java.util.Date;
//
//@Component
//public class JwtTokenProvider {
//
//    // Secret key for signing the JWT token
//    private String secretKey = "4bb6d1dfbafb64a681139d1586b6f1160d18159afd57c8c79136d7490630407c";  // Change to a more secure key
//
//    // Token expiration time (in milliseconds)
//    private long validityInMilliseconds = 3600000; // 1 hour
//
//    // Generate a token
//    public String createToken(String username) {
//        Claims claims = Jwts.claims().setSubject(username); // Set username as the subject
//        Date now = new Date();
//        Date validity = new Date(now.getTime() + validityInMilliseconds);
//
//        return Jwts.builder()
//                .setClaims(claims)
//                .setIssuedAt(now)
//                .setExpiration(validity)
//                .signWith(SignatureAlgorithm.HS256, secretKey)
//                .compact();
//    }
//
//    // Get the username (subject) from the token
//    public String getUsernameFromToken(String token) {
//        return Jwts.parser()
//                .setSigningKey(secretKey)
//                .parseClaimsJws(token)
//                .getBody()
//                .getSubject();
//    }
//
//    // Validate the token
//    public boolean validateToken(String token) {
//        try {
//            Jwts.parser().setSigningKey(secretKey).parseClaimsJws(token);
//            return true;
//        } catch (Exception e) {
//            // Token is invalid
//            return false;
//        }
//    }
//
//    // Get token expiration date
//    public Date getExpirationDateFromToken(String token) {
//        return Jwts.parser()
//                .setSigningKey(secretKey)
//                .parseClaimsJws(token)
//                .getBody()
//                .getExpiration();
//    }
//}

