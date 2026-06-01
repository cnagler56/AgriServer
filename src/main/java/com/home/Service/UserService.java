package com.home.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.home.Domain.LoginDTO;
import com.home.Domain.User;
import com.home.Repository.UserRepository;

@Service
public class UserService {

	private final UserRepository userRepository;
	private HashMap<UUID, Long> tokenMap;
	
	public UserService(UserRepository userRepository) {
		this.userRepository = userRepository;
		this.tokenMap = new HashMap();
	}
	
	
    public List<User> getList() {
        return this.userRepository.findAll();
    }
    
    public String getabcd() {
    	return "Hello from the back";
    }
    
    public void saveUser(User user) {
    	this.userRepository.save(user);
    	}
  
    
    
    public UUID getIn(LoginDTO use){
    	System.out.println("here UUID");
        Optional<User> maybeUser = this.userRepository.findByUsernameAndPassword(use.getUsername(),use.getPassword());
        if(maybeUser.isEmpty())
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found");
        UUID token = UUID.randomUUID();
        User user = maybeUser.get();
        this.tokenMap.put(token, user.getUserId());

        return token;
    }
    
    public User login(String email, String password){
    	System.out.println("here");
        Optional<User> maybeUser = this.userRepository.findByEmailAndPassword(email, password);
        if(maybeUser.isEmpty())
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found");
//        UUID token = UUID.randomUUID();
        User user = maybeUser.get();
//        this.tokenMap.put(token, user.getUserId());
        return user;
    }

    /**
     * Create a new user from the sign-up form.
     * Throws 409 CONFLICT if the email is already on file.
     */
    public User register(User incoming) {
        if (incoming.getEmail() == null || incoming.getEmail().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email is required");
        if (incoming.getPassword() == null || incoming.getPassword().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password is required");

        if (this.userRepository.findByEmail(incoming.getEmail()).isPresent())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with that email already exists");

        // Fall back username to the email if none was given
        if (incoming.getUsername() == null || incoming.getUsername().isBlank())
            incoming.setUsername(incoming.getEmail());

        // Combine first/last into the display "name" if the client didn't send one
        if (incoming.getName() == null || incoming.getName().isBlank()) {
            String combined = ((incoming.getFirstName() == null ? "" : incoming.getFirstName()) + " "
                              + (incoming.getLastName()  == null ? "" : incoming.getLastName())).trim();
            if (!combined.isEmpty()) incoming.setName(combined);
        }

        incoming.setActive(Boolean.TRUE);
        return this.userRepository.save(incoming);
    }
}
