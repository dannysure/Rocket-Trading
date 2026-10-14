package com.rockettrading.rocket_trading.repository.model;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class MarketSegmentActivityRecord {
    private String marketSegment;
    private long orderCount;
    private long fillCount;
    private BigDecimal totalNotional;
}
