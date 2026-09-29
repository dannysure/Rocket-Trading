package com.rockettrading.rocket_trading.repository.model;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ClientSegmentActivityRecord {
    private String riskProfile;
    private long clientCount;
    private long orderCount;
    private BigDecimal totalNotional;
}
