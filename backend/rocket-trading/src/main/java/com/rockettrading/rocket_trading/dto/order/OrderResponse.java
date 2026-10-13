package com.rockettrading.rocket_trading.dto.order;

import com.rockettrading.rocket_trading.repository.model.OrderRecord;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderResponse(
        long orderId,
        String symbol,
        String instrumentName,
        String market,
        String side,
        String orderType,
        BigDecimal quantity,
        BigDecimal limitPrice,
        String status,
        String rejectionReason,
        Instant submittedAt,
        // True while an accepted order waits for its market to publish a current price
        boolean awaitingMarket,
        String instrumentCurrency,
        BigDecimal executedPrice,
        BigDecimal settlementAmount,
        String settlementCurrency,
        Instant executedAt
) {
    public static OrderResponse from(OrderRecord order) {
        return new OrderResponse(
                order.getOrderId(),
                order.getSymbol(),
                order.getInstrumentName(),
                order.getMarket(),
                order.getOrderSide(),
                order.getOrderType(),
                order.getRequestedQuantity(),
                order.getLimitPrice(),
                order.getOrderStatus(),
                order.getRejectionReason(),
                order.getSubmittedAt(),
                "ACCEPTED".equals(order.getOrderStatus()) && order.getNextExecutionAt() != null,
                order.getInstrumentCurrency(),
                order.getExecutedPrice(),
                order.getSettlementAmount(),
                order.getSettlementCurrency(),
                order.getExecutedAt()
        );
    }
}
