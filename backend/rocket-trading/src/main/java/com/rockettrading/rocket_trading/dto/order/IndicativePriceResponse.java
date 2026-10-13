package com.rockettrading.rocket_trading.dto.order;

import java.math.BigDecimal;
import java.time.Instant;

/** What an order would cost right now, priced exactly as execution would price it (BR-13). */
public record IndicativePriceResponse(
        String symbol,
        String name,
        String side,
        BigDecimal quantity,
        BigDecimal unitPrice,
        String instrumentCurrency,
        BigDecimal fxRate,
        String settlementCurrency,
        BigDecimal estimatedTotal,
        BigDecimal availableCash,
        BigDecimal heldQuantity,
        Instant quoteCapturedAt,
        // False when the market is closed: the order will be accepted and filled at the next current price
        boolean marketOpen
) {
}
