package com.rockettrading.rocket_trading.dto.perps;

import java.math.BigDecimal;
import java.util.List;

public record PerpAccountResponse(
        long accountId,
        String currency,
        BigDecimal availableCollateral,
        BigDecimal totalMargin,
        BigDecimal unrealizedPnl,
        BigDecimal accountValue,
        List<PerpPositionResponse> positions
) {
}
