package com.rockettrading.rocket_trading.futures;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PerpetualFuturesApiClientTest {

    private final RestTemplate restTemplate = new RestTemplate();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
    private final PerpetualFuturesApiClient client = new PerpetualFuturesApiClient(restTemplate, "https://example.test");

    @AfterEach
    void verifyServer() {
        server.verify();
    }

    @Test
    @DisplayName("fetches best bid and ask from the order book endpoint")
    void fetchesBestBidAndAskFromTheOrderBookEndpoint() {
        server.expect(requestTo("https://example.test/futures/orderbook?symbol=BTCUSDT"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"bestBid\":\"100.50\",\"bestAsk\":\"100.75\"}",
                        MediaType.APPLICATION_JSON
                ));

        PerpetualFuturesOrderBook orderBook = client.fetchOrderBook("BTCUSDT");

        assertEquals(0, orderBook.bestBid().compareTo(new BigDecimal("100.50")));
        assertEquals(0, orderBook.bestAsk().compareTo(new BigDecimal("100.75")));
    }

    @Test
    @DisplayName("fetches the index price from the index price endpoint")
    void fetchesTheIndexPriceFromTheIndexPriceEndpoint() {
        server.expect(requestTo("https://example.test/futures/index-price?symbol=BTCUSDT"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"indexPrice\":\"100.00\"}",
                        MediaType.APPLICATION_JSON
                ));

        BigDecimal indexPrice = client.fetchIndexPrice("BTCUSDT");

        assertEquals(0, indexPrice.compareTo(new BigDecimal("100.00")));
    }
}
