package com.rockettrading.rocket_trading.dto.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/**
 * Event published when an order is rejected.
 * Consumed by notifications and audit services.
 */
public record OrderRejectedEvent(
        @JsonProperty("order_id")
        long orderId,
        
        @JsonProperty("client_id")
        long clientId,
        
        @JsonProperty("symbol")
        String symbol,
        
        @JsonProperty("rejection_reason")
        String rejectionReason,
        
        @JsonProperty("rejection_stage")
        String rejectionStage,  // "ACCEPTANCE" or "EXECUTION"
        
        @JsonProperty("rejected_at")
        Instant rejectedAt
) {
}
