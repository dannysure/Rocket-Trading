package com.rockettrading.rocket_trading.dto.order;

import com.rockettrading.rocket_trading.repository.model.FillRecord;

import java.math.BigDecimal;
import java.time.Instant;

public record FillResponse(
        long fillId,
        long orderId,
        BigDecimal executedQuantity,
        BigDecimal executedPrice,
        String instrumentCurrency,
        BigDecimal fxRate,
        BigDecimal settlementAmount,
        String settlementCurrency,
        Instant executedAt
) {
    public static FillResponse from(FillRecord fill) {
        return new FillResponse(
                fill.getFillId(),
                fill.getOrderId(),
                fill.getExecutedQuantity(),
                fill.getExecutedPrice(),
                fill.getInstrumentCurrency(),
                fill.getFxRate(),
                fill.getSettlementAmount(),
                fill.getSettlementCurrency(),
                fill.getExecutedAt()
        );
    }
}
