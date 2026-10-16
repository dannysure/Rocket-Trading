package com.rockettrading.rocket_trading.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.rockettrading.rocket_trading.dto.perps.PerpCandleResponse;
import com.rockettrading.rocket_trading.exception.ExternalServiceException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Historical spot candles from Coinbase Exchange's public REST API, for the perps chart. */
@Component
public class CoinbaseCandleClient {
    public static final Set<Integer> GRANULARITIES = Set.of(60, 300, 900, 3600);
    private static final Duration CACHE_TTL = Duration.ofSeconds(15);

    private final RestClient restClient;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    public CoinbaseCandleClient(RestClient coinbaseRestClient) {
        this.restClient = coinbaseRestClient;
    }

    /** Coinbase product for a crypto underlying (X:BTC-USD -> BTC-USD); null when it is not a Coinbase crypto pair. */
    public static String productId(String underlyingSymbol) {
        if (underlyingSymbol == null || !underlyingSymbol.toUpperCase().startsWith("X:")) return null;
        return underlyingSymbol.substring(2).toUpperCase();
    }

    /** Up to 300 most recent candles, oldest first; the last one is the interval still in progress. */
    public List<PerpCandleResponse> candles(String productId, int granularitySeconds) {
        if (!GRANULARITIES.contains(granularitySeconds)) {
            throw new IllegalArgumentException("interval must be one of 60, 300, 900 or 3600 seconds");
        }
        String key = productId + "|" + granularitySeconds;
        Cached cached = cache.get(key);
        if (cached != null && cached.storedAt().plus(CACHE_TTL).isAfter(Instant.now())) {
            return cached.candles();
        }
        try {
            JsonNode body = restClient.get()
                    .uri("/products/{product}/candles?granularity={granularity}", productId, granularitySeconds)
                    .retrieve()
                    .body(JsonNode.class);
            List<PerpCandleResponse> candles = parse(body);
            cache.put(key, new Cached(candles, Instant.now()));
            return candles;
        } catch (RestClientException | IllegalArgumentException exception) {
            throw new ExternalServiceException("PRICE_HISTORY_UNAVAILABLE", "Price history is unavailable right now");
        }
    }

    // Rows are [time, low, high, open, close, volume], newest first
    static List<PerpCandleResponse> parse(JsonNode body) {
        if (body == null || !body.isArray()) throw new IllegalArgumentException("Candles response is not an array");
        List<PerpCandleResponse> candles = new ArrayList<>();
        for (JsonNode row : body) {
            if (!row.isArray() || row.size() < 5) continue;
            candles.add(new PerpCandleResponse(row.get(0).asLong(), decimal(row.get(3)), decimal(row.get(2)),
                    decimal(row.get(1)), decimal(row.get(4))));
        }
        candles.sort(Comparator.comparingLong(PerpCandleResponse::time));
        return List.copyOf(candles);
    }

    private static BigDecimal decimal(JsonNode node) {
        return new BigDecimal(node.asText());
    }

    private record Cached(List<PerpCandleResponse> candles, Instant storedAt) {
    }
}
