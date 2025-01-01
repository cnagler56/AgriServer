package com.home.security;

public class SessionAuthenticationResponse {
    private String email;
    private String name;
    private String firstName;

    public SessionAuthenticationResponse(String email, String name) {
        this.email = email;
        this.name = name;
    }

    public String getFirstName() {
		return firstName;
	}

	public void setFirstName(String firstName) {
		this.firstName = firstName;
	}

	// Getters and Setters
    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
