package com.rockettrading.rocket_trading.repository.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
public class FillRecord {
    private Long fillId;
    private Long orderId;
    private BigDecimal executedQuantity;
    private BigDecimal executedPrice;
    private Long quoteId;
    private Instant executedAt;
}
