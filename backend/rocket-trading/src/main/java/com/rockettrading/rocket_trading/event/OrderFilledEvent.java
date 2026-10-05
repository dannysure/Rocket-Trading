package com.rockettrading.rocket_trading.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Event published when an order is filled (executed)
 * Topic: order-filled
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderFilledEvent {
    @JsonProperty("orderId")
    private long orderId;

    @JsonProperty("clientId")
    private long clientId;

    @JsonProperty("fillId")
    private long fillId;

    @JsonProperty("instrumentId")
    private long instrumentId;

    @JsonProperty("quantity")
    private BigDecimal quantity;

    @JsonProperty("fillPrice")
    private BigDecimal fillPrice;

    @JsonProperty("totalAmount")
    private BigDecimal totalAmount;

    @JsonProperty("accountId")
    private long accountId;

    @JsonProperty("filledAt")
    private Instant filledAt;

    @JsonProperty("eventId")
    private String eventId;
}
