package com.rockettrading.rocket_trading.dto.quote;

import com.rockettrading.rocket_trading.model.Quote;

import java.math.BigDecimal;
import java.time.Instant;

public record QuoteResponse(
        String symbol,
        BigDecimal bid,
        BigDecimal ask,
        BigDecimal price,
        long bidVolume,
        long askVolume,
        Instant capturedAt,
        String market,
        boolean delayed,
        String currency,
        BigDecimal changePercent,
        BigDecimal previousClose,
        // False when the price is older than the market's freshness window, e.g. while that market is closed
        boolean current
) {
    public static QuoteResponse from(Quote quote, String market, boolean current) {
        return new QuoteResponse(
                quote.getSymbol(),
                quote.getBid(),
                quote.getAsk(),
                quote.getPrice(),
                quote.getBidVolume(),
                quote.getAskVolume(),
                quote.getCapturedAt(),
                market,
                quote.isProviderStale(),
                quote.getCurrency(),
                quote.getChangePercent(),
                quote.getPreviousClose(),
                current
        );
    }
}
