package com.rackspace.mmi.telegrafhomebase;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class TelegrafHomebaseApplication {

	public static void main(String[] args) {
		SpringApplication.run(TelegrafHomebaseApplication.class, args);
	}
}
