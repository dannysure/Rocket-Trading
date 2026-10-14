package com.rockettrading.rocket_trading;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@org.springframework.scheduling.annotation.EnableScheduling
@MapperScan(basePackages = "com.rockettrading.rocket_trading.repository", annotationClass = Mapper.class)
@SpringBootApplication
public class RocketTradingApplication {

	public static void main(String[] args) {
		SpringApplication.run(RocketTradingApplication.class, args);
	}

}
