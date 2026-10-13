package com.rockettrading.rocket_trading.dto.reporting;

import com.rockettrading.rocket_trading.repository.model.InstrumentActivityRecord;

import java.math.BigDecimal;
import java.time.Instant;

public record InstrumentActivityResponse(
        String symbol,
        String assetClass,
        String segment,
        long orderCount,
        long fillCount,
        BigDecimal buyQuantity,
        BigDecimal sellQuantity,
        BigDecimal totalNotional,
        Instant lastSubmittedAt
) {
    public static InstrumentActivityResponse from(InstrumentActivityRecord record) {
        return new InstrumentActivityResponse(
                record.getSymbol(),
                record.getAssetClass(),
                record.getMarketSegment(),
                record.getOrderCount(),
                record.getFillCount(),
                record.getBuyQuantity(),
                record.getSellQuantity(),
                record.getTotalNotional(),
                record.getLastSubmittedAt()
        );
    }
}
