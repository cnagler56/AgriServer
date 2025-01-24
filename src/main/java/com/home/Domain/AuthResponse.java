package com.home.Domain;

public class AuthResponse {
    private String token;
    private MyUsers myUsers;

    public AuthResponse(String token, MyUsers myUsers) {
        this.token = token;
        this.myUsers = myUsers;
    }

	public String getToken() {
		return token;
	}

	public void setToken(String token) {
		this.token = token;
	}

	public MyUsers getUser() {
		return myUsers;
	}

	public void setUser(MyUsers myUsers) {
		this.myUsers = myUsers;
	}


}

