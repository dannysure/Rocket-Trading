package com.rockettrading.rocket_trading.dto.portfolio;

import com.rockettrading.rocket_trading.repository.model.HoldingRecord;

import java.math.BigDecimal;

public record PositionResponse(
        String symbol,
        BigDecimal quantity
) {
    public static PositionResponse from(HoldingRecord holdingRecord) {
        return new PositionResponse(holdingRecord.getTickerSymbol(), holdingRecord.getQuantity());
    }
}
