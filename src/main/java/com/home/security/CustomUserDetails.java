package com.home.security;

 
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.home.Domain.Role;
import com.home.Domain.MyUsers;

import ch.qos.logback.core.subst.Token;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
 
import java.util.Set;
import java.util.stream.Collectors;


public class CustomUserDetails implements UserDetails {

    private final MyUsers myUsers;  

    public CustomUserDetails(MyUsers myUsers) {
        this.myUsers = myUsers;
    }

    @Override
    public String getUsername() {
        return myUsers.getEmail();  
    }

    @Override
    public String getPassword() {
        return myUsers.getPassword();  
    }
    public String getEmail() {
    	return myUsers.getEmail();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // Convert user roles to GrantedAuthority
        return myUsers.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                .toList();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true; // Customize if you manage expiration
    }

    @Override
    public boolean isAccountNonLocked() {
        return true; // Customize if you manage locked accounts
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true; // Customize if credentials have expiration
    }

    @Override
    public boolean isEnabled() {
        return myUsers.getActive(); 
    }

    public MyUsers getUser() {
        return this.myUsers;  
    }
}



