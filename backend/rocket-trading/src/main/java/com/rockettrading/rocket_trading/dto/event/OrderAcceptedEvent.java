package com.rockettrading.rocket_trading.dto.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Event published when an order transitions from SUBMITTED to ACCEPTED status.
 * Triggers execution by consumer workers.
 */
public record OrderAcceptedEvent(
        @JsonProperty("order_id")
        long orderId,
        
        @JsonProperty("client_id")
        long clientId,
        
        @JsonProperty("account_id")
        long accountId,
        
        @JsonProperty("instrument_id")
        long instrumentId,
        
        @JsonProperty("symbol")
        String symbol,
        
        @JsonProperty("market")
        String market,
        
        @JsonProperty("side")
        String side,
        
        @JsonProperty("order_type")
        String orderType,
        
        @JsonProperty("requested_quantity")
        BigDecimal requestedQuantity,
        
        @JsonProperty("limit_price")
        BigDecimal limitPrice,
        
        @JsonProperty("accepted_at")
        Instant acceptedAt
) {
}
