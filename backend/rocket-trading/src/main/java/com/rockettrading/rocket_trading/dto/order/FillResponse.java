package com.rockettrading.rocket_trading.dto.order;

import com.rockettrading.rocket_trading.repository.model.FillRecord;

import java.math.BigDecimal;
import java.time.Instant;

public record FillResponse(
        long fillId,
        long orderId,
        BigDecimal executedQuantity,
        BigDecimal executedPrice,
        Instant executedAt
) {
    public static FillResponse from(FillRecord fillRecord) {
        return new FillResponse(
                fillRecord.getFillId(),
                fillRecord.getOrderId(),
                fillRecord.getExecutedQuantity(),
                fillRecord.getExecutedPrice(),
                fillRecord.getExecutedAt()
        );
    }
}
