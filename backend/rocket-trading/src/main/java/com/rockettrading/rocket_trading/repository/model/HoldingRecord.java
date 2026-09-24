package com.rockettrading.rocket_trading.repository.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
public class HoldingRecord {
    private Long holdingId;
    private Long accountId;
    private Long clientId;
    private Long instrumentId;
    private String tickerSymbol;
    private BigDecimal quantity;
    private Instant asOfTimestamp;
}
