package com.rockettrading.rocket_trading.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rockettrading.rocket_trading.config.PerpsProperties;
import com.rockettrading.rocket_trading.model.Quote;
import com.rockettrading.rocket_trading.repository.PerpMarketRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class CoinbaseTickerFeedTest {
    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-16T15:39:28Z"));
    private final CoinbaseTickerFeed feed = new CoinbaseTickerFeed(mock(PerpMarketRepository.class), new PerpsProperties(),
            new ObjectMapper(), clock);

    @Test void keepsTheLatestTopOfBookPerProduct() {
        feed.handle(ticker("BTC-USD", "81571.15", "81571.14", "81571.15"));
        feed.handle(ticker("BTC-USD", "81580.00", "81579.99", "81580.00"));
        feed.handle(ticker("ETH-USD", "2442.24", "2442.23", "2442.24"));

        Quote btc = feed.latest("X:BTC-USD").orElseThrow();
        assertEquals("X:BTC-USD", btc.getSymbol());
        assertEquals(new BigDecimal("81579.99"), btc.getBid());
        assertEquals(new BigDecimal("81580.00"), btc.getAsk());
        assertEquals(Instant.parse("2026-10-16T15:39:28.055078Z"), btc.getCapturedAt());
        assertEquals(new BigDecimal("2442.23"), feed.latest("X:ETH-USD").orElseThrow().getBid());
    }

    @Test void pricesOlderThanTheMaxAgeAreNotUsed() {
        feed.handle(ticker("BTC-USD", "100", "99", "100"));
        clock.advance(Duration.ofSeconds(30));
        assertTrue(feed.latest("X:BTC-USD").isPresent());
        clock.advance(Duration.ofSeconds(1));
        assertTrue(feed.latest("X:BTC-USD").isEmpty());
    }

    @Test void ignoresOtherMessagesMalformedTicksAndNonCryptoSymbols() {
        feed.handle("{\"type\":\"subscriptions\",\"channels\":[]}");
        feed.handle("{\"type\":\"heartbeat\",\"product_id\":\"BTC-USD\"}");
        feed.handle("not json");
        feed.handle(ticker("BTC-USD", "0", "0", "0"));
        // Crossed book
        feed.handle(ticker("BTC-USD", "100", "101", "99"));
        assertTrue(feed.latest("X:BTC-USD").isEmpty());
        assertTrue(feed.latest("AAPL").isEmpty());
        assertTrue(feed.latest(null).isEmpty());
    }

    private static String ticker(String product, String price, String bid, String ask) {
        return """
                {"type":"ticker","product_id":"%s","price":"%s","best_bid":"%s","best_ask":"%s","time":"2026-10-16T15:39:28.055078Z"}
                """.formatted(product, price, bid, ask);
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override public Instant instant() {
            return now;
        }
    }
}
