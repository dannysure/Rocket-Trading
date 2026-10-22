package com.rockettrading.rocket_trading.repository.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

/** One order as the reporting database stores it (BR-16). */
@Data
public class ReportingOrderFactRecord {
    private long orderId;
    private long clientId;
    private String riskProfile;
    private String symbol;
    private String assetClass;
    private String marketSegment;
    private String orderSide;
    private String orderStatus;
    private BigDecimal requestedQuantity;
    private Instant submittedAt;
    private Long fillId;
    private Instant executedAt;
    private BigDecimal settledNotional;
}
