package com.rockettrading.rocket_trading.futures;

import java.math.BigDecimal;

/**
 * Effect of one market fill. position is null when the fill leaves the account flat.
 * cashDelta is signed from the account's view: negative debits collateral, positive credits it.
 */
public record PerpFillOutcome(
        PerpPosition position,
        BigDecimal filledSize,
        BigDecimal realizedPnl,
        BigDecimal fee,
        BigDecimal cashDelta
) {
}
