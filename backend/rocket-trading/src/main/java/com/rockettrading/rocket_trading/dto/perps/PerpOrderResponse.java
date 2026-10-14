package com.rockettrading.rocket_trading.dto.perps;

import com.rockettrading.rocket_trading.repository.model.PerpOrderRecord;

import java.math.BigDecimal;
import java.time.Instant;

public record PerpOrderResponse(
        long orderId,
        String symbol,
        String side,
        String kind,
        BigDecimal requestedSize,
        BigDecimal filledSize,
        int leverage,
        boolean reduceOnly,
        String status,
        BigDecimal fillPrice,
        BigDecimal fee,
        BigDecimal realizedPnl,
        String rejectionReason,
        Instant submittedAt
) {
    public static PerpOrderResponse from(PerpOrderRecord order) {
        return new PerpOrderResponse(
                order.getPerpOrderId(),
                order.getMarketSymbol(),
                order.getOrderSide(),
                order.getOrderKind(),
                order.getRequestedSize(),
                order.getFilledSize(),
                order.getLeverage(),
                Boolean.TRUE.equals(order.getReduceOnly()),
                order.getOrderStatus(),
                order.getFillPrice(),
                order.getFee(),
                order.getRealizedPnl(),
                order.getRejectionReason(),
                order.getSubmittedAt()
        );
    }
}
