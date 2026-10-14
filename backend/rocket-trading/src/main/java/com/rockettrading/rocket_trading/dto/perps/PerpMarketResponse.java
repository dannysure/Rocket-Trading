package com.rockettrading.rocket_trading.dto.perps;

import java.math.BigDecimal;

public record PerpMarketResponse(
        String symbol,
        String baseAsset,
        String underlyingSymbol,
        String quoteCurrency,
        int maxLeverage,
        BigDecimal takerFeeRate,
        BigDecimal maintenanceMarginRate
) {
}
