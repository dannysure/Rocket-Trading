package com.rockettrading.rocket_trading.dto.portfolio;

import com.rockettrading.rocket_trading.repository.model.HoldingRecord;

import java.math.BigDecimal;

public record PositionResponse(
        String symbol,
        String name,
        String assetClass,
        String segment,
        String instrumentCurrency,
        BigDecimal quantity,
        // Per unit, in the account currency
        BigDecimal averageCost
) {
    public static PositionResponse from(HoldingRecord holding) {
        return new PositionResponse(
                holding.getTickerSymbol(),
                holding.getInstrumentName(),
                holding.getAssetClass(),
                holding.getMarketSegment(),
                holding.getBaseCurrency(),
                holding.getQuantity(),
                holding.getAverageCost()
        );
    }
}
