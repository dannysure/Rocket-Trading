package com.rockettrading.rocket_trading.dto.perps;

import java.math.BigDecimal;
import java.time.Instant;

/** Mark-dependent fields are null when the market's price is temporarily unavailable. */
public record PerpPositionResponse(
        long positionId,
        String symbol,
        String side,
        BigDecimal size,
        BigDecimal entryPrice,
        BigDecimal markPrice,
        BigDecimal positionValue,
        BigDecimal unrealizedPnl,
        BigDecimal returnOnEquity,
        BigDecimal liquidationPrice,
        BigDecimal margin,
        int leverage,
        BigDecimal cumulativeFunding,
        Instant openedAt
) {
}
