package com.rockettrading.rocket_trading.dto.reporting;

import com.rockettrading.rocket_trading.repository.model.ClientSegmentActivityRecord;

import java.math.BigDecimal;

public record ClientSegmentActivityResponse(
        String riskProfile,
        long clientCount,
        long orderCount,
        BigDecimal totalNotional
) {
    public static ClientSegmentActivityResponse from(ClientSegmentActivityRecord record) {
        return new ClientSegmentActivityResponse(
                record.getRiskProfile(),
                record.getClientCount(),
                record.getOrderCount(),
                record.getTotalNotional()
        );
    }
}
