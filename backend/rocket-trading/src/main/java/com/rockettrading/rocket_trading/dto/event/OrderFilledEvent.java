package com.rockettrading.rocket_trading.dto.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Event published when an order is successfully filled.
 * Consumed by analytics, notifications, and audit services.
 */
public record OrderFilledEvent(
        @JsonProperty("order_id")
        long orderId,
        
        @JsonProperty("client_id")
        long clientId,
        
        @JsonProperty("fill_id")
        long fillId,
        
        @JsonProperty("symbol")
        String symbol,
        
        @JsonProperty("side")
        String side,
        
        @JsonProperty("executed_quantity")
        BigDecimal executedQuantity,
        
        @JsonProperty("executed_price")
        BigDecimal executedPrice,
        
        @JsonProperty("executed_at")
        Instant executedAt,
        
        @JsonProperty("transaction_id")
        long transactionId
) {
}
