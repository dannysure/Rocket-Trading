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
        String market
) {
    public static QuoteResponse from(Quote quote, String market) {
        return new QuoteResponse(
                quote.getSymbol(),
                quote.getBid(),
                quote.getAsk(),
                quote.getPrice(),
                quote.getBidVolume(),
                quote.getAskVolume(),
                quote.getCapturedAt(),
                market
        );
    }
}
