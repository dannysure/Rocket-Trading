package com.rockettrading.rocket_trading.dto.reporting;

import com.rockettrading.rocket_trading.repository.model.MarketSegmentActivityRecord;

import java.math.BigDecimal;

public record MarketSegmentActivityResponse(String segment, long orderCount, long fillCount, BigDecimal totalNotional) {
    public static MarketSegmentActivityResponse from(MarketSegmentActivityRecord record) {
        return new MarketSegmentActivityResponse(record.getMarketSegment(), record.getOrderCount(), record.getFillCount(), record.getTotalNotional());
    }
}
