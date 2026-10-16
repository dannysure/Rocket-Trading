package com.rockettrading.rocket_trading.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rockettrading.rocket_trading.dto.perps.PerpCandleResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CoinbaseCandleClientTest {
    @Test void mapsCryptoUnderlyingsToCoinbaseProducts() {
        assertEquals("BTC-USD", CoinbaseCandleClient.productId("X:BTC-USD"));
        assertEquals("ETH-USD", CoinbaseCandleClient.productId("x:eth-usd"));
        assertNull(CoinbaseCandleClient.productId("AAPL"));
        assertNull(CoinbaseCandleClient.productId(null));
    }

    @Test void parsesRowsOldestFirstAsOpenHighLowClose() throws Exception {
        // Coinbase rows are [time, low, high, open, close, volume], newest first
        var body = new ObjectMapper().readTree("""
                [[1792015200,81332.15,81697.68,81671.3,81539.02,274.3],
                 [1792011600,81532.19,81864.98,81676.72,81671.29,153.2]]
                """);
        List<PerpCandleResponse> candles = CoinbaseCandleClient.parse(body);
        assertEquals(2, candles.size());
        PerpCandleResponse first = candles.get(0);
        assertEquals(1792011600L, first.time());
        assertEquals(new BigDecimal("81676.72"), first.open());
        assertEquals(new BigDecimal("81864.98"), first.high());
        assertEquals(new BigDecimal("81532.19"), first.low());
        assertEquals(new BigDecimal("81671.29"), first.close());
        assertEquals(1792015200L, candles.get(1).time());
    }

    @Test void rejectsUnsupportedIntervals() {
        CoinbaseCandleClient client = new CoinbaseCandleClient(org.springframework.web.client.RestClient.create());
        assertThrows(IllegalArgumentException.class, () -> client.candles("BTC-USD", 120));
    }
}
