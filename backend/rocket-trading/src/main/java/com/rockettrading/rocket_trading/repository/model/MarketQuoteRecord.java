package com.rockettrading.rocket_trading.repository.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
public class MarketQuoteRecord {
    private Long quoteId;
    private Long instrumentId;
    private BigDecimal bidPrice;
    private BigDecimal askPrice;
    private Instant quoteTimestamp;
}
