package com.arkil;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ArkilServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(ArkilServerApplication.class, args);
	}

}
