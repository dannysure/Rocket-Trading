package com.rockettrading.rocket_trading.dto.quote;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CandleResponse(LocalDate date, BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close) {
}
