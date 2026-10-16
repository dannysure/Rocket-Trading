package com.rockettrading.rocket_trading.dto.perps;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * oraclePrice is the spot index; markPrice (the perp price) and bid/ask carry this exchange's premium over it, which
 * comes from the long/short open-interest imbalance. fundingRate is charged on the hour, (mark - spot) / spot / 8;
 * positive means longs pay shorts.
 */
public record PerpTickerResponse(
        String symbol,
        BigDecimal markPrice,
        BigDecimal oraclePrice,
        BigDecimal bid,
        BigDecimal ask,
        BigDecimal premium,
        BigDecimal fundingRate,
        Instant nextFundingAt,
        BigDecimal openInterest,
        BigDecimal longOpenInterest,
        BigDecimal shortOpenInterest,
        int maxLeverage,
        BigDecimal takerFeeRate,
        BigDecimal maintenanceMarginRate,
        Instant capturedAt,
        boolean delayed
) {
}
