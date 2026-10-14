package com.rockettrading.rocket_trading.repository.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
public class FillRecord {
    private Long fillId;
    private Long orderId;
    private BigDecimal executedQuantity;
    // In the instrument's currency
    private BigDecimal executedPrice;
    private Long quoteId;
    private Instant executedAt;
    private String instrumentCurrency;
    private String settlementCurrency;
    // Instrument currency to settlement currency; 1 when they match
    private BigDecimal fxRate;
    // Cash moved, in the account's currency
    private BigDecimal settlementAmount;
}
