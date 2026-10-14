package com.rockettrading.rocket_trading.dto.portfolio;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** The book marked to the latest market prices, in the account currency. Indicative, unlike the summary. */
public record PortfolioValuationResponse(
        String currency,
        BigDecimal cashBalance,
        BigDecimal holdingsValue,
        BigDecimal totalValue,
        BigDecimal netDeposits,
        BigDecimal totalReturn,
        BigDecimal dayChange,
        // False when at least one position could not be priced; totals then exclude it
        boolean complete,
        Instant valuedAt,
        List<PositionValuation> positions
) {
    public record PositionValuation(
            String symbol,
            String name,
            String assetClass,
            String segment,
            BigDecimal quantity,
            BigDecimal averageCost,
            String instrumentCurrency,
            BigDecimal price,
            BigDecimal fxRate,
            BigDecimal marketValue,
            BigDecimal unrealizedReturn,
            BigDecimal changePercent,
            Instant priceAsOf,
            boolean priced
    ) {
    }
}
