package com.rockettrading.rocket_trading.repository.model;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ReportingOverviewRecord {
    private long totalOrders;
    private long acceptedOrders;
    private long filledOrders;
    private long rejectedOrders;
    private long totalFills;
    private BigDecimal totalNotional;
    private long activeClients;
}
