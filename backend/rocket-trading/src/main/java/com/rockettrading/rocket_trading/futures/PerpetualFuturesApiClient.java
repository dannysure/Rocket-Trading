package com.rockettrading.rocket_trading.futures;

import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.util.Objects;

public class PerpetualFuturesApiClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public PerpetualFuturesApiClient(RestTemplate restTemplate, String baseUrl) {
        this.restTemplate = Objects.requireNonNull(restTemplate, "restTemplate");
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl");
    }

    public PerpetualFuturesOrderBook fetchOrderBook(String symbol) {
        OrderBookResponse response = restTemplate.getForObject(
                uri("/futures/orderbook", symbol),
                OrderBookResponse.class
        );

        if (response == null) {
            throw new IllegalStateException("Order book API returned no body");
        }

        return new PerpetualFuturesOrderBook(response.bestBid(), response.bestAsk());
    }

    public BigDecimal fetchIndexPrice(String symbol) {
        IndexPriceResponse response = restTemplate.getForObject(
                uri("/futures/index-price", symbol),
                IndexPriceResponse.class
        );

        if (response == null) {
            throw new IllegalStateException("Index price API returned no body");
        }

        return response.indexPrice();
    }

    private String uri(String path, String symbol) {
        return UriComponentsBuilder.fromHttpUrl(baseUrl)
                .path(path)
                .queryParam("symbol", symbol)
                .toUriString();
    }

    private record OrderBookResponse(BigDecimal bestBid, BigDecimal bestAsk) {
    }

    private record IndexPriceResponse(BigDecimal indexPrice) {
    }
}
