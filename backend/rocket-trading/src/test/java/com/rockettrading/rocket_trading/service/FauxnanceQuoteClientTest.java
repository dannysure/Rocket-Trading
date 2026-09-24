package com.rockettrading.rocket_trading.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rockettrading.rocket_trading.config.FauxnanceProperties;
import com.rockettrading.rocket_trading.model.Quote;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class FauxnanceQuoteClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void treatsProviderMarkedFreshQuoteAsCurrentEvenWhenCachedTimestampIsOlderThanThreshold() throws Exception {
        FauxnanceProperties properties = new FauxnanceProperties();
        properties.setApiKey("test-key");
        properties.setMaxAgeSeconds(60);
        FauxnanceQuoteClient client = new FauxnanceQuoteClient(mock(RestClient.class), properties);

        String response = """
                {
                  "data": {
                    "symbol": "MSFT",
                    "price": 496.015,
                    "bid": 495.95,
                    "ask": 496.08,
                    "asOf": "2026-09-24T17:45:13Z"
                  },
                  "meta": {
                    "asOf": "2026-09-24T17:45:13Z",
                    "source": "cache",
                    "stale": false
                  }
                }
                """;

        Quote quote = client.mapQuoteResponse(objectMapper.readTree(response), "MSFT");

        assertEquals("MSFT", quote.getSymbol());
        assertTrue(quote.getCapturedAt().isAfter(Instant.now().minusSeconds(5)));
    }
}
