package com.ai.meetsage;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication
public class MeetsageApplication {

	public static void main(String[] args) {
		SpringApplication.run(MeetsageApplication.class, args);
	}

}