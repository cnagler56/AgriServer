package com.home;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AgriServerApplication {

	public static void main(String[] args) {
		// Boot diagnostic: show exactly what the container received for the datasource
		// (host/port/db only — no password) so deploy issues are unambiguous.
		System.out.println("[BOOT] SPRING_DATASOURCE_URL=" + System.getenv("SPRING_DATASOURCE_URL"));
		System.out.println("[BOOT] SPRING_DATASOURCE_USERNAME set? " + (System.getenv("SPRING_DATASOURCE_USERNAME") != null));
		System.out.println("[BOOT] SPRING_DATASOURCE_PASSWORD set? " + (System.getenv("SPRING_DATASOURCE_PASSWORD") != null));
		System.out.println("[BOOT] PORT=" + System.getenv("PORT"));
		SpringApplication.run(AgriServerApplication.class, args);
		System.out.println("..........................Running........................");
	}

}