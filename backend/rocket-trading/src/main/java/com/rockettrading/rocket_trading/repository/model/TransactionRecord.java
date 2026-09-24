package com.rockettrading.rocket_trading.repository.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
public class TransactionRecord {
    private Long transactionId;
    private Long accountId;
    private Long instrumentId;
    private Long fillId;
    private String transactionType;
    private BigDecimal quantity;
    private BigDecimal unitPrice;
    private BigDecimal netAmount;
    private Instant transactionDate;
}
