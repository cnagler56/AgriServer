package com.home.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.home.Domain.LoginDTO;
import com.home.Domain.User;
import com.home.Repository.UserRepository;

/**
 * Account management. Passwords are hashed with BCrypt at registration time
 * and verified with {@link PasswordEncoder#matches} on login — the database
 * never stores plaintext.
 *
 * Migration note: existing rows with plaintext passwords (pre-BCrypt) will
 * fail to log in after this change because `matches(rawPassword, plaintext)`
 * returns false. Either drop them or have those users sign up again.
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final HashMap<UUID, Long> tokenMap = new HashMap<>();

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<User> getList() {
        return this.userRepository.findAll();
    }

    public void saveUser(User user) {
        this.userRepository.save(user);
    }

    /** Legacy session-style login by username. Kept for the /getIn endpoint. */
    public UUID getIn(LoginDTO use) {
        User user = this.userRepository.findByUsername(use.getUsername())
            .filter(u -> u.getPassword() != null
                      && passwordEncoder.matches(use.getPassword(), u.getPassword()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));

        UUID token = UUID.randomUUID();
        this.tokenMap.put(token, user.getUserId());
        return token;
    }

    /**
     * Email + password sign-in. Returns the user record on success; throws
     * 401 on either missing email or wrong password.
     *
     * The unified error message is intentional — distinguishing "no such email"
     * from "wrong password" leaks which addresses have accounts.
     */
    public User login(String email, String password) {
        Optional<User> maybeUser = this.userRepository.findByEmail(email);
        if (maybeUser.isEmpty()
            || maybeUser.get().getPassword() == null
            || !passwordEncoder.matches(password, maybeUser.get().getPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }
        return maybeUser.get();
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
        if (incoming.getPassword().length() < 6)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be at least 6 characters");

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

        // Hash the password — the raw value never hits the database
        incoming.setPassword(passwordEncoder.encode(incoming.getPassword()));
        incoming.setActive(Boolean.TRUE);
        return this.userRepository.save(incoming);
    }
}
