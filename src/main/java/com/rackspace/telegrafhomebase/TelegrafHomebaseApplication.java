package com.rackspace.telegrafhomebase;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.Random;

@SpringBootApplication
public class TelegrafHomebaseApplication {

	public static void main(String[] args) {
		SpringApplication.run(TelegrafHomebaseApplication.class, args);
	}

	@Bean
	public Random rand() {
		return new Random();
	}
}
