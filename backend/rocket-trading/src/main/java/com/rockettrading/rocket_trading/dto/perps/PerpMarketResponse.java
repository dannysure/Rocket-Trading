package com.rockettrading.rocket_trading.dto.perps;

import java.math.BigDecimal;

/** skewScale and maxPremium let clients estimate a fill's premium the same way the server prices it. */
public record PerpMarketResponse(
        String symbol,
        String baseAsset,
        String underlyingSymbol,
        String quoteCurrency,
        int maxLeverage,
        BigDecimal takerFeeRate,
        BigDecimal maintenanceMarginRate,
        BigDecimal skewScale,
        BigDecimal maxPremium
) {
}
