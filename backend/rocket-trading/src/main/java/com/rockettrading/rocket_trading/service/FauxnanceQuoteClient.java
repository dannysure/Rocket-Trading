package com.rockettrading.rocket_trading.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.rockettrading.rocket_trading.config.FauxnanceProperties;
import com.rockettrading.rocket_trading.exception.ExternalServiceException;
import com.rockettrading.rocket_trading.model.Quote;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeParseException;

@Component
public class FauxnanceQuoteClient implements QuoteProvider {
    private final RestClient restClient;
    private final FauxnanceProperties fauxnanceProperties;

    public FauxnanceQuoteClient(RestClient fauxnanceRestClient, FauxnanceProperties fauxnanceProperties) {
        this.restClient = fauxnanceRestClient;
        this.fauxnanceProperties = fauxnanceProperties;
    }

    @Override
    public Quote fetchQuote(String symbol, String market) {
        if (!StringUtils.hasText(fauxnanceProperties.getApiKey())) {
            throw new ExternalServiceException("QUOTE_PROVIDER_NOT_CONFIGURED",
                    "Fauxnance API key is missing. Set FAUXNANCE_API_KEY in your .env file");
        }

        try {
            String path = quotePathForMarket(market);
            String uri = UriComponentsBuilder
                    .fromPath(path)
                    .buildAndExpand(symbol.toUpperCase())
                    .toUriString();

            JsonNode body = restClient.get()
                    .uri(uri)
                    .header("X-Api-Key", fauxnanceProperties.getApiKey())
                    .retrieve()
                    .body(JsonNode.class);

            if (body == null) {
                throw new ExternalServiceException("PRICE_UNAVAILABLE", "Fauxnance returned an empty quote response");
            }

            return mapQuoteResponse(body, symbol.toUpperCase());
        } catch (RestClientException exception) {
            throw new ExternalServiceException("PRICE_UNAVAILABLE",
                    "Could not retrieve a quote from Fauxnance at this time");
        }
    }

    Quote mapQuoteResponse(JsonNode body, String fallbackSymbol) {
        JsonNode payload = body.has("data") ? body.get("data") : body;
        JsonNode metadata = body.has("meta") ? body.get("meta") : body;
        BigDecimal price = extractRequiredDecimal(payload, "price", "last", "lastPrice", "quote", "value");
        BigDecimal bid = extractOptionalDecimal(payload, price, "bid", "bestBid", "bidPrice");
        BigDecimal ask = extractOptionalDecimal(payload, price, "ask", "bestAsk", "askPrice");
        Instant capturedAt = extractInstantOrNull(metadata, "asOf", "timestamp", "capturedAt");
        if (capturedAt == null) {
            capturedAt = extractInstantOrNull(payload, "timestamp", "capturedAt", "asOf");
        }
        if (capturedAt == null) {
            capturedAt = Instant.now();
        }
        capturedAt = normalizeFreshCapturedAt(metadata, capturedAt);

        return new Quote(
                extractText(payload, "symbol", fallbackSymbol),
                bid,
                ask,
                price,
                extractLong(payload, "bidVolume", "bid_size", "bestBidSize"),
                extractLong(payload, "askVolume", "ask_size", "bestAskSize"),
                capturedAt
        );
    }

    private String quotePathForMarket(String market) {
        if ("crypto".equalsIgnoreCase(market)) {
            return fauxnanceProperties.getCryptoPath();
        }
        if ("stock".equalsIgnoreCase(market)) {
            return fauxnanceProperties.getStockPath();
        }
        throw new IllegalArgumentException("market must be stock or crypto");
    }

    private BigDecimal extractRequiredDecimal(JsonNode payload, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode value = payload.get(fieldName);
            if (value != null && !value.isNull()) {
                return new BigDecimal(value.asText());
            }
        }
        throw new ExternalServiceException("PRICE_UNAVAILABLE", "Fauxnance quote response is missing price fields");
    }

    private BigDecimal extractOptionalDecimal(JsonNode payload, BigDecimal fallback, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode value = payload.get(fieldName);
            if (value != null && !value.isNull()) {
                return new BigDecimal(value.asText());
            }
        }
        return fallback;
    }

    private Instant normalizeFreshCapturedAt(JsonNode metadata, Instant capturedAt) {
        JsonNode stale = metadata.get("stale");
        if (stale != null
                && stale.isBoolean()
                && !stale.asBoolean()
                && capturedAt.isBefore(Instant.now().minusSeconds(fauxnanceProperties.getMaxAgeSeconds()))) {
            return Instant.now();
        }
        return capturedAt;
    }

    private long extractLong(JsonNode payload, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode value = payload.get(fieldName);
            if (value != null && !value.isNull()) {
                return value.asLong();
            }
        }
        return 0L;
    }

    private Instant extractInstant(JsonNode payload, String... fieldNames) {
        Instant value = extractInstantOrNull(payload, fieldNames);
        return value == null ? Instant.now() : value;
    }

    private Instant extractInstantOrNull(JsonNode payload, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode value = payload.get(fieldName);
            if (value != null && !value.isNull()) {
                try {
                    if (value.isNumber()) {
                        return Instant.ofEpochSecond(value.asLong());
                    }
                    return Instant.parse(value.asText());
                } catch (DateTimeParseException ignored) {
                    // fall through to next supported field
                }
            }
        }
        return null;
    }

    private String extractText(JsonNode payload, String fieldName, String fallback) {
        JsonNode value = payload.get(fieldName);
        if (value == null || value.isNull() || value.asText().isBlank()) {
            return fallback;
        }
        return value.asText();
    }
}
