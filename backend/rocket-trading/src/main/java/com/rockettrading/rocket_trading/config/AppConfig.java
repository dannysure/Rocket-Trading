package com.rockettrading.rocket_trading.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties({JwtProperties.class, FauxnanceProperties.class, NewsProperties.class, PerpsProperties.class})
public class AppConfig {

    @Bean
    RestClient fauxnanceRestClient(RestClient.Builder builder, FauxnanceProperties fauxnanceProperties) {
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(java.time.Duration.ofSeconds(3));
        factory.setReadTimeout(java.time.Duration.ofSeconds(5));
        return builder.requestFactory(factory).baseUrl(fauxnanceProperties.getBaseUrl()).build();
    }

    @Bean
    RestClient newsRestClient(RestClient.Builder builder, NewsProperties newsProperties) {
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(java.time.Duration.ofSeconds(3));
        factory.setReadTimeout(java.time.Duration.ofSeconds(5));
        return builder.requestFactory(factory).baseUrl(newsProperties.getBaseUrl()).build();
    }
  
    @Bean
    RestClient coinbaseRestClient(RestClient.Builder builder, PerpsProperties perpsProperties) {
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(java.time.Duration.ofSeconds(3));
        factory.setReadTimeout(java.time.Duration.ofSeconds(5));
        return builder.requestFactory(factory).baseUrl(perpsProperties.getPriceFeed().getRestUrl()).build();
    }
}
