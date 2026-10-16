package com.rockettrading.rocket_trading.dto.perps;

import java.math.BigDecimal;

/** One OHLC candle of the spot index; time is the interval start in Unix seconds. */
public record PerpCandleResponse(long time, BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close) {
}
