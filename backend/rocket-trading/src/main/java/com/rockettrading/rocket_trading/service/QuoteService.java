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
        Quote quote = quoteProvider.fetchQuote(symbol, market);
        if (!quote.isCurrent(Instant.now(), fauxnanceProperties.getMaxAgeSeconds())) {
            throw new ConflictException("QUOTE_STALE", "The retrieved quote is stale and cannot be used");
        }
        return QuoteResponse.from(quote, market.toLowerCase());
    }
}
