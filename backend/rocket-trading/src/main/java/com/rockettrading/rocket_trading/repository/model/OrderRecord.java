package com.rockettrading.rocket_trading.repository.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
public class OrderRecord {
    private String market;
    private String idempotencyKey;
    private String requestFingerprint;
    private Long orderId;
    private Long clientId;
    private Long accountId;
    private Long instrumentId;
    private String symbol;
    private String orderSide;
    private String orderType;
    private BigDecimal requestedQuantity;
    private BigDecimal limitPrice;
    private String orderStatus;
    private String rejectionReason;
    private Instant submittedAt;
    // Set while an accepted order waits for a current market price
    private Instant nextExecutionAt;

    // Read-only context joined from the instrument and fill for display
    private String instrumentName;
    private String instrumentCurrency;
    private BigDecimal executedPrice;
    private BigDecimal settlementAmount;
    private String settlementCurrency;
    private Instant executedAt;
}
