package com.rockettrading.rocket_trading.repository.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
public class OrderRecord {
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
}
