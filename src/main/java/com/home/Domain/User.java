package com.home.Domain;

import java.util.Collection;
import java.util.List;

//import org.springframework.security.core.GrantedAuthority;
//import org.springframework.security.core.userdetails.UserDetails;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
//public class User implements UserDetails {
	public class User  {

	@Override
	public String toString() {
		return "User [userId=" + userId + ", firstName=" + firstName + ", lastName=" + lastName + ", username="
				+ username + ", name=" + name + ", email=" + email + ", city=" + city + ", state=" + state
				+ ", password=" + password + ", interest=" + interest + ", active=" + active + ", roles=" + roles
				+ ", tokens=" + tokens + "]";
	}

	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	@JsonProperty
	@Column(name = "userid")
	Long userId;

	@Column(name = "first_name")
	@JsonProperty
	String firstName;

	@JsonProperty
	@Column(name = "last_name")
	String lastName;

	@Column(name = "username")
	private String username;

	@JsonProperty
	String name;

	@JsonProperty
	String email;

	@JsonProperty
	String city;

	@JsonProperty
	String state;

	@JsonProperty
	String password;

	@JsonProperty
	String interest;

	@JsonProperty
	Boolean active;

	@Enumerated(value = EnumType.STRING)
	Role roles;

	@OneToMany(mappedBy = "user")
	private List<Token> tokens;

	public Long getUserId() {
		return userId;
	}

	public void setUserId(Long userId) {
		this.userId = userId;
	}

	public String getFirstName() {
		return firstName;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Boolean getActive() {
		return active;
	}

	public void setActive(Boolean active) {
		this.active = active;
	}

	public Role getRoles() {
		return roles;
	}

	public void setRole(Role roles) {
		this.roles = roles;
	}

	public void setFirstName(String firstName) {
		this.firstName = firstName;
	}

	public String getLastName() {
		return lastName;
	}

	public void setLastName(String lastName) {
		this.lastName = lastName;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getCity() {
		return city;
	}

	public void setCity(String city) {
		this.city = city;
	}

	public String getState() {
		return state;
	}

	public void setState(String state) {
		this.state = state;
	}

	public List<Token> getTokens() {
		return tokens;
	}

	public void setTokens(List<Token> tokens) {
		this.tokens = tokens;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public void setRoles(Role roles) {
		this.roles = roles;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public String getInterest() {
		return interest;
	}

	public void setInterest(String interest) {
		this.interest = interest;
	}

//	@Override
//	public Collection<? extends GrantedAuthority> getAuthorities() {
//		// TODO Auto-generated method stub
//		return null;
//	}

//	@Override
//	public String getUsername() {
//		// TODO Auto-generated method stub
//		return null;
//	}

//	@Override
//	public boolean isAccountNonExpired() {
//		// TODO Auto-generated method stub
//		return false;
//	}

	public String getUsername() {
		return username;
	}

//	@Override
//	public boolean isAccountNonLocked() {
//		// TODO Auto-generated method stub
//		return false;
//	}

//	@Override
//	public boolean isCredentialsNonExpired() {
//		// TODO Auto-generated method stub
//		return false;
//	}

//	@Override
//	public boolean isEnabled() {
//		// TODO Auto-generated method stub
//		return false;
//	}

	public Long getUserId1() {
		// TODO Auto-generated method stub
		return null;
	}

}
