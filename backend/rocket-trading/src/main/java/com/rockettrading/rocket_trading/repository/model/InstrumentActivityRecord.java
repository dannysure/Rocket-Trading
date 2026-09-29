package com.rockettrading.rocket_trading.repository.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
public class InstrumentActivityRecord {
    private String symbol;
    private String assetClass;
    private long orderCount;
    private long fillCount;
    private BigDecimal buyQuantity;
    private BigDecimal sellQuantity;
    private Instant lastSubmittedAt;
}
