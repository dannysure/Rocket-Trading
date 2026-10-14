package com.rockettrading.rocket_trading.repository.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
public class PerpOrderRecord {
    private Long perpOrderId;
    private Long clientId;
    private Long accountId;
    private Long marketId;
    private String marketSymbol;
    private Long positionId;
    private String orderSide;
    private String orderKind;
    private BigDecimal requestedSize;
    private BigDecimal filledSize;
    private Integer leverage;
    private Boolean reduceOnly;
    private String orderStatus;
    private BigDecimal fillPrice;
    private BigDecimal fee;
    private BigDecimal realizedPnl;
    private String rejectionReason;
    private String idempotencyKey;
    private String requestFingerprint;
    private Instant submittedAt;
}
