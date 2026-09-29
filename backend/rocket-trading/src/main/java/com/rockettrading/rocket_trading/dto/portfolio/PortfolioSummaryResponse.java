package com.rockettrading.rocket_trading.dto.portfolio;

import java.math.BigDecimal;
import java.util.List;

public record PortfolioSummaryResponse(
        long clientId,
        long accountId,
        BigDecimal cashBalance,
        String currency,
        List<PositionResponse> positions
) {
}
