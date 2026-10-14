package com.rockettrading.rocket_trading.repository.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class OpsOrderSummaryRecord extends OrderRecord {
    private String clientName;
    private String clientEmail;
}
