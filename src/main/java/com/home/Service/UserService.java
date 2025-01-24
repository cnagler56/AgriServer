package com.home.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.home.Domain.LoginDTO;
import com.home.Domain.MyUsers;
import com.home.Repository.UserRepository;

@Service
public class UserService {

	private final UserRepository userRepository;
	private HashMap<UUID, Long> tokenMap;
	
	public UserService(UserRepository userRepository) {
		this.userRepository = userRepository;
		this.tokenMap = new HashMap();
	}
	
	
    public List<MyUsers> getList() {
        return this.userRepository.findAll();
    }
    
    
    public void saveUser(MyUsers myUsers) {
    	this.userRepository.save(myUsers);
    	}

}