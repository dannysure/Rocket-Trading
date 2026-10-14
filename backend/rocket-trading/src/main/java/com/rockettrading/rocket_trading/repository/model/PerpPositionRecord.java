package com.rockettrading.rocket_trading.repository.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
public class PerpPositionRecord {
    private Long positionId;
    private Long clientId;
    private Long accountId;
    private Long marketId;
    private String marketSymbol;
    private String positionSide;
    private BigDecimal size;
    private BigDecimal entryPrice;
    private BigDecimal isolatedMargin;
    private Integer leverage;
    private String positionStatus;
    private BigDecimal realizedPnl;
    private BigDecimal cumulativeFunding;
    private Instant openedAt;
    private Instant updatedAt;
    private Instant closedAt;
}
