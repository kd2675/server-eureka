package com.server.eureka;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableEurekaServer
public class ServerEurekaApplication {

	public static void main(String[] args) {
		SpringApplication.run(ServerEurekaApplication.class, args);
	}

}
