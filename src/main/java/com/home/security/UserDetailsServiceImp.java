//package com.home.security;
//
// 
//
//
//
//import com.home.Domain.User;
//import com.home.Repository.UserRepository;
//
//import java.util.Collection;
//import java.util.List;
//import java.util.stream.Collectors;
//
//import org.springframework.security.core.GrantedAuthority;
//import org.springframework.security.core.userdetails.UserDetails;
//import org.springframework.security.core.userdetails.UserDetailsService;
//import org.springframework.security.core.userdetails.UsernameNotFoundException;
//import org.springframework.stereotype.Service;
//
//@Service
//public class UserDetailsServiceImp implements UserDetailsService {
//
//    private final UserRepository repository;
//
//    public UserDetailsServiceImp(UserRepository repository) {
//        this.repository = repository;
//    }
//
//    @Override
//    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
//        return repository.findByUsername(username)
//                .orElseThrow(()-> new UsernameNotFoundException("User not found"));
//    }
//}