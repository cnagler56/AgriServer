package com.home;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;

@SpringBootApplication(exclude = {SecurityAutoConfiguration.class})
public class AgriServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(AgriServerApplication.class, args);
		System.out.println("..........................Running........................");
	}

}