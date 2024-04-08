package com.home.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.home.Domain.User;
import com.home.Repository.UserRepository;

@Service
public class UserService {

	private final UserRepository userRepository;
	private HashMap<UUID, Long> tokenMap;
	
	public UserService(UserRepository userRepository) {
		this.userRepository = userRepository;
	}
	
	
    public List<User> getList() {
        return this.userRepository.findAll();
    }
    
    public void saveUser(User user) {
    	this.userRepository.save(user);
    	}
    
//    public UUID login(String email, String password){
//        Optional<User> maybeUser = this.userRepository.findByEmailAndPassword(email, password);
//        if(maybeUser.isEmpty())
//            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found");
//        UUID token = UUID.randomUUID();
//        User user = maybeUser.get();
////        this.tokenMap.put(token, user.getUserId());
//        return token;
//    }
    
    public User login(String email, String password){
        Optional<User> maybeUser = this.userRepository.findByEmailAndPassword(email, password);
        if(maybeUser.isEmpty())
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found");
//        UUID token = UUID.randomUUID();
        User user = maybeUser.get();
//        this.tokenMap.put(token, user.getUserId());
        return user;
    }
}
