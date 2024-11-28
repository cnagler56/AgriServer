//package com.home.Service;
//
//import java.util.ArrayList;
//
//import javax.security.auth.login.AccountNotFoundException;
//
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.security.core.userdetails.UserDetails;
//import org.springframework.security.core.userdetails.UserDetailsService;
//import org.springframework.security.core.userdetails.UsernameNotFoundException;
//import org.springframework.stereotype.Service;
//
//import com.home.Domain.User;
//import com.home.Repository.UserRepository;
//
//
//@Service
//public class CustomUserDetailsService implements UserDetailsService {
//
//    @Autowired
//    private UserRepository userRepository;
//
//  
//    public UserDetails loadUserByEmail(String email) throws AccountNotFoundException {
//        User user = userRepository.findByEmail(email)
//            .orElseThrow(() -> new AccountNotFoundException("User not found with email: " + email));
//        	System.out.println(user);
////        return new org.springframework.security.core.userdetails.User(
////            user.getEmail(),
////            user.getPassword(),
////            new ArrayList<>()
////            return UserDetailsImpl.build(user);
//            return (UserDetails) user;
//            
////        );
//    }
//
//	@Override
//	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
//		// TODO Auto-generated method stub
//		return null;
//	}
//}
//
