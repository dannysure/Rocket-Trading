package com.rockettrading.rocket_trading.dto.perps;

import java.math.BigDecimal;
import java.time.Instant;

public record PerpTickerResponse(
        String symbol,
        BigDecimal markPrice,
        BigDecimal oraclePrice,
        BigDecimal bid,
        BigDecimal ask,
        BigDecimal fundingRate,
        Instant nextFundingAt,
        BigDecimal openInterest,
        int maxLeverage,
        BigDecimal takerFeeRate,
        BigDecimal maintenanceMarginRate,
        Instant capturedAt,
        boolean delayed
) {
}
