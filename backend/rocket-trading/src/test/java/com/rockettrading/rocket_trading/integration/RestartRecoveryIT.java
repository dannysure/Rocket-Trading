package com.rockettrading.rocket_trading.integration;

import com.rockettrading.rocket_trading.RocketTradingApplication;
import com.rockettrading.rocket_trading.dto.auth.RegisterClientRequest;
import com.rockettrading.rocket_trading.dto.order.SubmitOrderRequest;
import com.rockettrading.rocket_trading.model.Quote;
import com.rockettrading.rocket_trading.service.AuthService;
import com.rockettrading.rocket_trading.service.OrderService;
import com.rockettrading.rocket_trading.service.QuoteProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class RestartRecoveryIT {
    @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");
    @TestConfiguration static class Quotes {
        @Bean @Primary QuoteProvider fixtureQuotes() {
            return (symbol, market) -> new Quote(symbol, new BigDecimal("100"), new BigDecimal("101"), new BigDecimal("101"), 100, 100, Instant.now());
        }
    }

    @Test void restartPreservesSettledTradeAndRecoversPendingOrderWithoutDuplicates() {
        long client;
        long pending;
        try (var app = start(false)) {
            client = app.getBean(AuthService.class).register(new RegisterClientRequest("Restart", "restart@example.com", null, null, new BigDecimal("10000"))).clientId();
            var orders = app.getBean(OrderService.class);
            var request = new SubmitOrderRequest("AAPL", "BUY", BigDecimal.ONE, "stock", "MARKET", null);
            long settled = orders.submitOrder(client, request, "settled").orderId();
            orders.executeOrder(settled);
            pending = orders.submitOrder(client, request, "pending").orderId();
            assertEquals("ACCEPTED", orders.getOrder(client, pending).status());
        }
        try (var app = start(true)) {
            var db = app.getBean(JdbcTemplate.class);
            await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                    assertEquals("FILLED", app.getBean(OrderService.class).getOrder(client, pending).status()));
            assertEquals(2, db.queryForObject("SELECT count(*) FROM fills", Integer.class));
            assertEquals(new BigDecimal("9798.0000"), db.queryForObject("SELECT cash_balance FROM client_accounts", BigDecimal.class));
        }
        try (var app = start(false)) {
            var db = app.getBean(JdbcTemplate.class);
            assertEquals(2, db.queryForObject("SELECT count(*) FROM orders", Integer.class));
            assertEquals(2, db.queryForObject("SELECT count(*) FROM fills", Integer.class));
            assertEquals(new BigDecimal("9798.0000"), db.queryForObject("SELECT cash_balance FROM client_accounts", BigDecimal.class));
            assertEquals(5, db.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE success", Integer.class));
        }
    }

    private ConfigurableApplicationContext start(boolean worker) {
        return new SpringApplicationBuilder(RocketTradingApplication.class, Quotes.class).run(
                "--server.port=0", "--spring.datasource.url=" + postgres.getJdbcUrl(),
                "--spring.datasource.username=" + postgres.getUsername(), "--spring.datasource.password=" + postgres.getPassword(),
                "--trading.worker.enabled=" + worker, "--trading.worker.delay-ms=100", "--spring.main.banner-mode=off");
    }
}
