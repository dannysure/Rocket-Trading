package com.rockettrading.rocket_trading.service;

import com.rockettrading.rocket_trading.config.FauxnanceProperties;
import com.rockettrading.rocket_trading.dto.quote.QuoteResponse;
import com.rockettrading.rocket_trading.exception.ConflictException;
import com.rockettrading.rocket_trading.model.Quote;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class QuoteService {
    private final QuoteProvider quoteProvider;
    private final FauxnanceProperties fauxnanceProperties;

    public QuoteService(QuoteProvider quoteProvider, FauxnanceProperties fauxnanceProperties) {
        this.quoteProvider = quoteProvider;
        this.fauxnanceProperties = fauxnanceProperties;
    }

    public QuoteResponse getQuote(String symbol, String market) {
        return QuoteResponse.from(fetchCurrentQuote(symbol, market), market.toLowerCase());
    }

    public Quote fetchCurrentQuote(String symbol, String market) {
        if (!"stock".equalsIgnoreCase(market) && !"crypto".equalsIgnoreCase(market)) {
            throw new IllegalArgumentException("market must be stock or crypto");
        }
        Quote quote = quoteProvider.fetchQuote(symbol, market);
        if (!quote.getSymbol().equalsIgnoreCase(symbol) || quote.getAsk().compareTo(quote.getBid()) < 0) {
            throw new ConflictException("QUOTE_INVALID", "Quote symbol or bid/ask is invalid");
        }
        for (var amount : java.util.List.of(quote.getBid(), quote.getAsk())) {
            var stored = amount.setScale(4, java.math.RoundingMode.HALF_UP);
            if (stored.signum() <= 0 || stored.precision() > 16) {
                throw new ConflictException("QUOTE_INVALID", "Quote is outside supported price precision");
            }
        }
        if (quote.getCapturedAt().isAfter(Instant.now().plusSeconds(5)) ||
                !quote.isCurrent(Instant.now(), fauxnanceProperties.getMaxAgeSeconds())) {
            throw new ConflictException("QUOTE_STALE", "Quote timestamp " + quote.getCapturedAt() + " is not current");
        }
        return quote;
    }
}
