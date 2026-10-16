package com.rockettrading.rocket_trading.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rockettrading.rocket_trading.model.Quote;
import com.rockettrading.rocket_trading.service.OrderService;
import com.rockettrading.rocket_trading.service.QuoteProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BR-02 with the order cache switched on, as it is in every real deployment. The other integration
 * tests run without Redis, which is how a cache keyed only by order id once leaked orders between clients.
 */
@Testcontainers
@SpringBootTest(properties = {"trading.worker.enabled=false", "spring.kafka.enabled=false", "spring.redis.enabled=true",
        "reporting.refresh-ms=3600000", "fixtures.password=", "catalog.sync.enabled=false",
        "quotes.fauxnance.max-age-seconds=900"})
@AutoConfigureMockMvc
class OrderCacheOwnershipIT {
    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");
    @Container @ServiceConnection(name = "redis")
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired OrderService service;
    @Autowired RedisTemplate<String, Object> redisTemplate;
    @MockitoBean QuoteProvider quotes;

    @BeforeEach void quotes() {
        doAnswer(i -> new Quote(i.getArgument(0), new BigDecimal("100"), new BigDecimal("101"), new BigDecimal("101"), 100, 100, Instant.now()))
                .when(quotes).fetchQuote(anyString(), anyString());
    }

    @Test void anotherClientCannotReadACachedOrderItsFillsOrItsTimeline() throws Exception {
        String owner = signedInClient();
        String intruder = signedInClient();
        long orderId = placeOrder(owner);
        service.executeOrder(orderId);

        // The owner's read puts the finished order in the cache
        mvc.perform(get("/api/v1/orders/" + orderId).header("Authorization", owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("FILLED"));
        assertFalse(redisTemplate.keys("order:*").isEmpty());

        mvc.perform(get("/api/v1/orders/" + orderId).header("Authorization", intruder)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/fills/" + orderId).header("Authorization", intruder)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/orders/" + orderId + "/timeline").header("Authorization", intruder)).andExpect(status().isNotFound());

        mvc.perform(get("/api/v1/orders/" + orderId).header("Authorization", owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("FILLED"));
    }

    @Test void anOrderInFlightIsNeverCachedSoItsStatusIsNeverStale() throws Exception {
        String owner = signedInClient();
        long orderId = placeOrder(owner);
        mvc.perform(get("/api/v1/orders/" + orderId).header("Authorization", owner))
                .andExpect(jsonPath("$.data.status").value("ACCEPTED"));
        assertTrue(redisTemplate.keys("order:*:" + orderId).isEmpty());

        service.executeOrder(orderId);
        mvc.perform(get("/api/v1/orders/" + orderId).header("Authorization", owner))
                .andExpect(jsonPath("$.data.status").value("FILLED"));
    }

    private long placeOrder(String bearer) throws Exception {
        var response = mvc.perform(post("/api/v1/orders").header("Authorization", bearer)
                        .header("Idempotency-Key", UUID.randomUUID().toString()).contentType("application/json")
                        .content("{\"symbol\":\"AAPL\",\"side\":\"BUY\",\"quantity\":1,\"market\":\"stock\",\"orderType\":\"MARKET\"}"))
                .andExpect(status().isAccepted()).andReturn();
        return mapper.readTree(response.getResponse().getContentAsString()).path("data").path("orderId").asLong();
    }

    private String signedInClient() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        String credentials = "{\"email\":\"" + email + "\",\"password\":\"test-password-123\"";
        mvc.perform(post("/api/v1/auth/register").contentType("application/json")
                .content(credentials + ",\"name\":\"Test\",\"initialCash\":1000}")).andExpect(status().isCreated());
        JsonNode session = mapper.readTree(mvc.perform(post("/api/v1/auth/sign-in").contentType("application/json")
                .content(credentials + "}")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        return "Bearer " + session.path("data").path("accessToken").asText();
    }
}
