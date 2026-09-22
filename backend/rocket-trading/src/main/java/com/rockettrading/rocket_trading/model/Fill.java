package com.rockettrading.rocket_trading.model;

import java.math.BigDecimal;
import java.time.Instant;

public class Fill {
    private long fillId;
    private BigDecimal executionPrice;
    private long executedQuantity;
    private Instant executedAt;
}

