package com.rockettrading.rocket_trading.dto.portfolio;

import java.math.BigDecimal;
import java.util.List;

/** The client's book as of their last executed trade (BR-10): exact, never priced live. */
public record PortfolioSummaryResponse(
        long clientId,
        long accountId,
        BigDecimal cashBalance,
        String currency,
        BigDecimal netDeposits,
        List<PositionResponse> positions
) {
}
