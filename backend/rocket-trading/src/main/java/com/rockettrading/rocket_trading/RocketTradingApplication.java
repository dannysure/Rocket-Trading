package com.rockettrading.rocket_trading;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@org.springframework.scheduling.annotation.EnableScheduling
@SpringBootApplication
public class RocketTradingApplication {

	public static void main(String[] args) {
		SpringApplication.run(RocketTradingApplication.class, args);
	}

}
