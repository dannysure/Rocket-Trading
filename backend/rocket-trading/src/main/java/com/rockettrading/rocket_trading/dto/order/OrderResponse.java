package com.rockettrading.rocket_trading.dto.order;

import com.rockettrading.rocket_trading.repository.model.OrderRecord;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderResponse(
        long orderId,
        String symbol,
        String side,
        String orderType,
        BigDecimal quantity,
        String status,
        String rejectionReason,
        Instant submittedAt
) {
    public static OrderResponse from(OrderRecord orderRecord) {
        return new OrderResponse(
                orderRecord.getOrderId(),
                orderRecord.getSymbol(),
                orderRecord.getOrderSide(),
                orderRecord.getOrderType(),
                orderRecord.getRequestedQuantity(),
                orderRecord.getOrderStatus(),
                orderRecord.getRejectionReason(),
                orderRecord.getSubmittedAt()
        );
    }
}
