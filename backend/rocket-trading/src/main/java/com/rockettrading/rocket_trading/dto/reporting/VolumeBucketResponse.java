package com.rockettrading.rocket_trading.dto.reporting;

import com.rockettrading.rocket_trading.repository.model.VolumeBucketRecord;

import java.math.BigDecimal;
import java.time.Instant;

public record VolumeBucketResponse(Instant periodStart, long orderCount, long fillCount, BigDecimal totalNotional) {
    public static VolumeBucketResponse from(VolumeBucketRecord record) {
        return new VolumeBucketResponse(record.getPeriodStart(), record.getOrderCount(), record.getFillCount(), record.getTotalNotional());
    }
}
