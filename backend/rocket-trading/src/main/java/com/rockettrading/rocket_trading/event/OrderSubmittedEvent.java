package com.rockettrading.rocket_trading.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Event published when an order is submitted by a client
 * Topic: order-submitted
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderSubmittedEvent {
    @JsonProperty("orderId")
    private long orderId;

    @JsonProperty("clientId")
    private long clientId;

    @JsonProperty("instrumentId")
    private long instrumentId;

    @JsonProperty("side")
    private String side; // BUY or SELL

    @JsonProperty("quantity")
    private BigDecimal quantity;

    @JsonProperty("accountId")
    private long accountId;

    @JsonProperty("timestamp")
    private Instant timestamp;

    @JsonProperty("eventId")
    private String eventId;

    @JsonProperty("idempotencyKey")
    private String idempotencyKey;
}
