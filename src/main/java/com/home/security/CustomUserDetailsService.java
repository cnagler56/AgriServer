package com.home.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import com.home.Domain.MyUsers;
import com.home.Repository.UserRepository;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository myUsersRepository;

    public CustomUserDetailsService(UserRepository myUsersRepository) {
        this.myUsersRepository = myUsersRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        MyUsers user = myUsersRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
        
        return new CustomUserDetails(user);  // Return the custom UserDetails implementation
    }
}
