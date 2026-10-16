package com.rockettrading.rocket_trading.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rockettrading.rocket_trading.model.Quote;
import com.rockettrading.rocket_trading.repository.ReportingStore;
import com.rockettrading.rocket_trading.service.OrderService;
import com.rockettrading.rocket_trading.service.QuoteProvider;
import com.rockettrading.rocket_trading.service.ReportingSnapshot;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BR-16 with reporting on its own database server, as docker compose runs it: analyst queries keep
 * answering while the trading tables are locked, and the trading database holds no reporting data.
 */
@Testcontainers
@SpringBootTest(properties = {"trading.worker.enabled=false", "spring.kafka.enabled=false", "spring.redis.enabled=false",
        "reporting.refresh-ms=3600000", "reporting.sync.settle-seconds=0", "fixtures.password=", "catalog.sync.enabled=false",
        "quotes.fauxnance.max-age-seconds=900"})
@AutoConfigureMockMvc
class ReportingIsolationIT {
    @Container @ServiceConnection
    static PostgreSQLContainer<?> trading = new PostgreSQLContainer<>("postgres:15-alpine");
    @Container
    static PostgreSQLContainer<?> reportingDb = new PostgreSQLContainer<>("postgres:15-alpine").withDatabaseName("rocket_reporting");

    @DynamicPropertySource
    static void reportingDatabase(DynamicPropertyRegistry registry) {
        registry.add("reporting.datasource.url", reportingDb::getJdbcUrl);
        registry.add("reporting.datasource.username", reportingDb::getUsername);
        registry.add("reporting.datasource.password", reportingDb::getPassword);
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired OrderService service;
    @Autowired ReportingSnapshot reporting;
    @Autowired ReportingStore store;
    @Autowired DataSource tradingDataSource;
    @Autowired JdbcTemplate tradingDb;
    @MockitoBean QuoteProvider quotes;

    @Test void analystsReadTheirOwnDatabaseAndKeepWorkingWhileTradingTablesAreLocked() throws Exception {
        doAnswer(i -> new Quote(i.getArgument(0), new BigDecimal("100"), new BigDecimal("101"), new BigDecimal("101"), 100, 100, Instant.now()))
                .when(quotes).fetchQuote(anyString(), anyString());
        String client = signIn(register("joanna-" + UUID.randomUUID() + "@example.com"));
        long filled = placeOrder(client, "BUY", "2");
        service.executeOrder(filled);
        placeOrder(client, "SELL", "5"); // rejected: holds only 2
        reporting.refresh();

        assertTrue(store.isSeparateDatabase());
        assertFalse(tradingDb.queryForObject(
                "select exists(select 1 from information_schema.schemata where schema_name = 'reporting')", Boolean.class));
        assertFalse(tradingDb.queryForObject(
                "select exists(select 1 from pg_matviews where matviewname = 'reporting_order_facts')", Boolean.class));
        try (Connection reportingConnection = DriverManager.getConnection(reportingDb.getJdbcUrl(), reportingDb.getUsername(), reportingDb.getPassword())) {
            var rs = reportingConnection.createStatement().executeQuery("select count(*) from reporting.order_facts");
            rs.next();
            assertEquals(2, rs.getInt(1));
        }

        String analyst = staff("ANALYST");
        // Lock every table an analyst query would need on the trading database, as a long settlement might
        try (Connection lock = tradingDataSource.getConnection()) {
            lock.setAutoCommit(false);
            lock.createStatement().execute("lock table orders, fills, client_profiles, financial_instruments in access exclusive mode");
            long started = System.nanoTime();
            mvc.perform(get("/api/v1/reporting/overview").header("Authorization", analyst))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalOrders").value(2))
                    .andExpect(jsonPath("$.data.filledOrders").value(1))
                    .andExpect(jsonPath("$.data.rejectedOrders").value(1))
                    .andExpect(jsonPath("$.data.totalNotional").value(202))
                    .andExpect(jsonPath("$.data.dataAsOf").exists());
            mvc.perform(get("/api/v1/reporting/instruments").header("Authorization", analyst))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].symbol").value("AAPL"));
            mvc.perform(get("/api/v1/reporting/volume?bucket=day").header("Authorization", analyst))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].orderCount").value(2));
            assertTrue((System.nanoTime() - started) / 1_000_000 < 5_000, "reporting waited on the trading database");
            lock.rollback();
        }
    }

    private long placeOrder(String bearer, String side, String quantity) throws Exception {
        var response = mvc.perform(post("/api/v1/orders").header("Authorization", bearer)
                        .header("Idempotency-Key", UUID.randomUUID().toString()).contentType("application/json")
                        .content("{\"symbol\":\"AAPL\",\"side\":\"" + side + "\",\"quantity\":" + quantity + ",\"market\":\"stock\",\"orderType\":\"MARKET\"}"))
                .andReturn();
        return mapper.readTree(response.getResponse().getContentAsString()).path("data").path("orderId").asLong();
    }

    private String staff(String role) throws Exception {
        String email = role.toLowerCase() + "-" + UUID.randomUUID() + "@rocket.internal";
        register(email);
        tradingDb.update("update client_profiles set user_role = ? where email_address = ?", role, email);
        return signIn(email);
    }

    private String register(String email) throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType("application/json")
                .content("{\"name\":\"Test\",\"email\":\"" + email + "\",\"password\":\"test-password-123\",\"initialCash\":1000}"))
                .andExpect(status().isCreated());
        return email;
    }

    private String signIn(String email) throws Exception {
        var body = mvc.perform(post("/api/v1/auth/sign-in").contentType("application/json")
                .content("{\"email\":\"" + email + "\",\"password\":\"test-password-123\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + mapper.readTree(body).path("data").path("accessToken").asText();
    }
}
