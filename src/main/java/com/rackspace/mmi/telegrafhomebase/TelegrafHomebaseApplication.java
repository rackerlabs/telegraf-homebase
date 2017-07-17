package com.rackspace.mmi.telegrafhomebase;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@SpringBootApplication
@EnableAsync
public class TelegrafHomebaseApplication {

	public static void main(String[] args) {
		SpringApplication.run(TelegrafHomebaseApplication.class, args);
	}

	@Bean
	public TaskExecutor asyncExecutor() {
		final ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setThreadNamePrefix("async-");
		executor.initialize();
		return executor;
	}
}
