package com.rockettrading.rocket_trading.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties({JwtProperties.class, FauxnanceProperties.class})
public class AppConfig {

    @Bean
    RestClient fauxnanceRestClient(RestClient.Builder builder, FauxnanceProperties fauxnanceProperties) {
        return builder.baseUrl(fauxnanceProperties.getBaseUrl()).build();
    }
}
