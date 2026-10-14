package com.rockettrading.rocket_trading.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.rockettrading.rocket_trading.config.FauxnanceProperties;
import com.rockettrading.rocket_trading.dto.quote.CandleResponse;
import com.rockettrading.rocket_trading.exception.ExternalServiceException;
import com.rockettrading.rocket_trading.model.Quote;
import com.rockettrading.rocket_trading.repository.model.FinancialInstrumentRecord;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

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
        } catch (RestClientException | IllegalArgumentException | java.time.DateTimeException exception) {
            throw new ExternalServiceException("PRICE_UNAVAILABLE",
                    "Could not retrieve a quote from Fauxnance at this time");
        }
    }

    @Override
    public FinancialInstrumentRecord lookupInstrument(String symbol) {
        if (!StringUtils.hasText(fauxnanceProperties.getApiKey())) {
            throw new ExternalServiceException("QUOTE_PROVIDER_NOT_CONFIGURED",
                    "Fauxnance API key is missing. Set FAUXNANCE_API_KEY in your .env file");
        }
        try {
            String uri = UriComponentsBuilder.fromPath("/v1/symbols/{symbol}")
                    .buildAndExpand(symbol.toUpperCase())
                    .toUriString();
            JsonNode body = restClient.get()
                    .uri(uri)
                    .header("X-Api-Key", fauxnanceProperties.getApiKey())
                    .retrieve()
                    .body(JsonNode.class);
            JsonNode data = body == null ? null : body.get("data");
            // An instrument is only usable when Fauxnance itself says which currency it trades in
            if (data == null || !data.hasNonNull("symbol") || !StringUtils.hasText(data.path("currency").asText(""))) {
                return null;
            }
            FinancialInstrumentRecord instrument = new FinancialInstrumentRecord();
            instrument.setTickerSymbol(data.get("symbol").asText());
            instrument.setInstrumentName(data.path("name").asText(data.get("symbol").asText()));
            instrument.setAssetClass(assetClassFor(data.path("type").asText()));
            instrument.setBaseCurrency(data.get("currency").asText().toUpperCase());
            instrument.setTradable(data.path("active").asBoolean(false));
            instrument.setListingExchange(data.path("exchange").asText(null));
            instrument.setMarketSegment(segmentFor(instrument.getAssetClass(), instrument.getListingExchange()));
            return instrument;
        } catch (HttpClientErrorException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
                return null;
            }
            throw new ExternalServiceException("SYMBOL_LOOKUP_FAILED", "Could not look up the symbol with Fauxnance");
        } catch (RestClientException exception) {
            throw new ExternalServiceException("SYMBOL_LOOKUP_FAILED",
                    "Symbol lookup is temporarily unavailable; please retry");
        }
    }

    @Override
    public List<CandleResponse> fetchDailyCandles(String symbol, LocalDate from) {
        if (!StringUtils.hasText(fauxnanceProperties.getApiKey())) {
            return List.of();
        }
        try {
            String uri = UriComponentsBuilder.fromPath("/v1/candles/{symbol}")
                    .queryParam("from", from.toString())
                    .buildAndExpand(symbol.toUpperCase())
                    .toUriString();
            JsonNode body = restClient.get()
                    .uri(uri)
                    .header("X-Api-Key", fauxnanceProperties.getApiKey())
                    .retrieve()
                    .body(JsonNode.class);
            return mapCandles(body, from);
        } catch (RestClientException | IllegalArgumentException | java.time.DateTimeException exception) {
            // History only decorates the trade screen; pricing never depends on it
            return List.of();
        }
    }

    /**
     * Real daily candles only. Fauxnance marks days it estimated rather than observed as synthetic
     * (typically today's unfinished session); those are left out so the chart never shows a price
     * that did not trade.
     */
    List<CandleResponse> mapCandles(JsonNode body, LocalDate from) {
        JsonNode candles = body == null ? null : body.path("data").path("candles");
        List<CandleResponse> result = new ArrayList<>();
        if (candles == null || !candles.isArray()) {
            return result;
        }
        for (JsonNode candle : candles) {
            if (!candle.hasNonNull("date") || !candle.hasNonNull("close")) continue;
            if (candle.path("synthetic").asBoolean(false)) continue;
            LocalDate date = LocalDate.parse(candle.get("date").asText());
            if (date.isBefore(from)) continue;
            result.add(new CandleResponse(
                    date,
                    decimalOrNull(candle, "open"),
                    decimalOrNull(candle, "high"),
                    decimalOrNull(candle, "low"),
                    new BigDecimal(candle.get("close").asText())
            ));
        }
        return result;
    }

    private BigDecimal decimalOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : new BigDecimal(value.asText());
    }

    // NSE/BSE listings are Indian equities, LSE listings UK equities; FX and crypto keep their own segment
    private String segmentFor(String assetClass, String exchange) {
        if ("Crypto".equals(assetClass)) return "CRYPTO";
        if ("FX".equals(assetClass)) return "FX";
        if (exchange == null) return "US";
        return switch (exchange.toUpperCase()) {
            case "NSE", "BSE" -> "IN";
            case "LSE", "LON" -> "UK";
            default -> "US";
        };
    }

    // Maps Fauxnance registry types onto financial_instruments.asset_class; ETFs trade like equities here
    private String assetClassFor(String type) {
        return switch (type) {
            case "crypto" -> "Crypto";
            case "fx" -> "FX";
            default -> "Equity";
        };
    }

    Quote mapQuoteResponse(JsonNode body, String fallbackSymbol) {
        JsonNode payload = body.has("data") ? body.get("data") : body;
        JsonNode metadata = body.has("meta") ? body.get("meta") : body;
        // Fauxnance flags stale whenever its cache missed a refresh; QuoteService's max-age check still bounds how old a price may be
        boolean providerStale = metadata.path("stale").asBoolean(false);
        if (providerStale && !fauxnanceProperties.isAllowStale()) {
            throw new ExternalServiceException("QUOTE_STALE", "Provider marked the quote stale");
        }
        BigDecimal price = extractRequiredDecimal(payload, "price", "last", "lastPrice", "quote", "value");
        BigDecimal bid = extractOptionalDecimal(payload, price, "bid", "bestBid", "bidPrice");
        BigDecimal ask = extractOptionalDecimal(payload, price, "ask", "bestAsk", "askPrice");
        Instant capturedAt = extractInstantOrNull(metadata, "asOf", "timestamp", "capturedAt");
        if (capturedAt == null) {
            capturedAt = extractInstantOrNull(payload, "timestamp", "capturedAt", "asOf");
        }
        if (capturedAt == null) {
            throw new ExternalServiceException("QUOTE_INVALID", "Quote must include a valid source timestamp");
        }

        Quote quote = new Quote(
                extractText(payload, "symbol", fallbackSymbol),
                bid,
                ask,
                price,
                extractLong(payload, "bidVolume", "bid_size", "bestBidSize"),
                extractLong(payload, "askVolume", "ask_size", "bestAskSize"),
                capturedAt
        );
        quote.setProviderStale(providerStale);
        quote.setCurrency(extractText(payload, "currency", null));
        quote.setChangePercent(extractOptionalDecimal(payload, null, "changePercent"));
        quote.setPreviousClose(extractOptionalDecimal(payload, null, "previousClose"));
        return quote;
    }

    private String quotePathForMarket(String market) {
        if ("crypto".equalsIgnoreCase(market)) {
            return fauxnanceProperties.getCryptoPath();
        }
        // FX pairs are served from the same quote path as equities
        if ("stock".equalsIgnoreCase(market) || "fx".equalsIgnoreCase(market)) {
            return fauxnanceProperties.getStockPath();
        }
        throw new IllegalArgumentException("market must be stock, crypto or fx");
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

    private long extractLong(JsonNode payload, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode value = payload.get(fieldName);
            if (value != null && !value.isNull()) {
                return value.asLong();
            }
        }
        return 0L;
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
