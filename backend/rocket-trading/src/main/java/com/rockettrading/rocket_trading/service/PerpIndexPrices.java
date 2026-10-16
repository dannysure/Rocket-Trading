package com.rockettrading.rocket_trading.service;

import com.rockettrading.rocket_trading.config.PerpsProperties;
import com.rockettrading.rocket_trading.exception.ExternalServiceException;
import com.rockettrading.rocket_trading.model.Quote;
import com.rockettrading.rocket_trading.repository.model.PerpMarketRecord;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spot index quotes for perps: the live Coinbase stream when it is current, otherwise Fauxnance. Fallback
 * results, including failures, are reused briefly so an outage of the stream cannot exhaust the Fauxnance quota.
 */
@Component
public class PerpIndexPrices {
    private final ObjectProvider<CoinbaseTickerFeed> feed;
    private final QuoteService quoteService;
    private final Duration fallbackTtl;
    private final Map<String, Fallback> fallbacks = new ConcurrentHashMap<>();

    public PerpIndexPrices(ObjectProvider<CoinbaseTickerFeed> feed, QuoteService quoteService, PerpsProperties properties) {
        this.feed = feed;
        this.quoteService = quoteService;
        this.fallbackTtl = Duration.ofMillis(properties.getFallbackCacheMs());
    }

    /** live is false when the quote came from the fallback provider. */
    public record IndexQuote(Quote quote, boolean live) {
    }

    public IndexQuote quote(PerpMarketRecord market) {
        CoinbaseTickerFeed stream = feed.getIfAvailable();
        Optional<Quote> streamed = stream == null ? Optional.empty() : stream.latest(market.getUnderlyingSymbol());
        if (streamed.isPresent()) return new IndexQuote(streamed.get(), true);
        return new IndexQuote(fallback(market), false);
    }

    private Quote fallback(PerpMarketRecord market) {
        String key = market.getUnderlyingMarket() + "|" + market.getUnderlyingSymbol();
        Fallback cached = fallbacks.get(key);
        if (cached != null && cached.at().plus(fallbackTtl).isAfter(Instant.now())) {
            if (cached.quote() == null) {
                throw new ExternalServiceException("PRICE_UNAVAILABLE", "The price feed for " + market.getMarketSymbol() + " is unavailable");
            }
            return cached.quote();
        }
        try {
            Quote quote = quoteService.fetchCurrentQuote(market.getUnderlyingSymbol(), market.getUnderlyingMarket());
            fallbacks.put(key, new Fallback(quote, Instant.now()));
            return quote;
        } catch (RuntimeException unavailable) {
            fallbacks.put(key, new Fallback(null, Instant.now()));
            throw unavailable;
        }
    }

    private record Fallback(Quote quote, Instant at) {
    }
}
