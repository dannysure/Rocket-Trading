package com.rockettrading.rocket_trading.futures;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public record PerpMarketSpec(String symbol, int maxLeverage, BigDecimal takerFeeRate) {
    public PerpMarketSpec {
        Objects.requireNonNull(symbol, "symbol");
        Objects.requireNonNull(takerFeeRate, "takerFeeRate");
        if (maxLeverage < 1) throw new IllegalArgumentException("maxLeverage must be at least 1");
    }

    /** Half of the initial margin required at max leverage, so a max-leverage position has a buffer before liquidation. */
    public BigDecimal maintenanceMarginRate() {
        return BigDecimal.ONE.divide(BigDecimal.valueOf(2L * maxLeverage), PerpPosition.RATIO_SCALE, RoundingMode.HALF_UP);
    }
}
