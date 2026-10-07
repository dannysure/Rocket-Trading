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
    void preservesSourceTimestampEvenWhenProviderMarksOldQuoteFresh() throws Exception {
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
        assertEquals(Instant.parse("2026-09-24T17:45:13Z"), quote.getCapturedAt());
    }

    @Test
    void rejectsMissingTimestampsAndExplicitlyStalePayloads() throws Exception {
        FauxnanceQuoteClient client = new FauxnanceQuoteClient(mock(RestClient.class), new FauxnanceProperties());
        for (String json : java.util.List.of("{\"price\":100}", "{\"price\":100,\"stale\":true,\"timestamp\":\"2026-01-01T00:00:00Z\"}")) {
            var payload = objectMapper.readTree(json);
            org.junit.jupiter.api.Assertions.assertThrows(com.rockettrading.rocket_trading.exception.ExternalServiceException.class,
                    () -> client.mapQuoteResponse(payload, "AAPL"));
        }
    }

    @Test
    void acceptsAndFlagsProviderStaleQuotesWhenAllowed() throws Exception {
        FauxnanceProperties properties = new FauxnanceProperties();
        properties.setAllowStale(true);
        FauxnanceQuoteClient client = new FauxnanceQuoteClient(mock(RestClient.class), properties);

        Quote quote = client.mapQuoteResponse(
                objectMapper.readTree("{\"price\":100,\"stale\":true,\"timestamp\":\"2026-01-01T00:00:00Z\"}"), "AAPL");

        assertTrue(quote.isProviderStale());
        assertEquals(Instant.parse("2026-01-01T00:00:00Z"), quote.getCapturedAt());
    }
}
