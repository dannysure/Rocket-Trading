package com.rockettrading.rocket_trading.repository.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
public class VolumeBucketRecord {
    private Instant periodStart;
    private long orderCount;
    private long fillCount;
    private BigDecimal totalNotional;
}
