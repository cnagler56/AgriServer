package com.home.Service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.home.Domain.User;
import com.home.Repository.UserRepository;

@Service
public class UserService {

	private final UserRepository userRepository;
	
	public UserService(UserRepository userRepository) {
		this.userRepository = userRepository;
	}
	
	
    public List<User> getList() {
        return this.userRepository.findAll();
    }
}
