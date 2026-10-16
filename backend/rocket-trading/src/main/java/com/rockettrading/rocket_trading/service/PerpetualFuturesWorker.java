package com.rockettrading.rocket_trading.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "trading.worker.enabled", havingValue = "true", matchIfMissing = true)
public class PerpetualFuturesWorker {
    private final PerpetualFuturesSettlement settlement;

    // Checked often, not just once at the interval start, so a funding charge that fails (no price, a restart)
    // is retried until it succeeds; each position is charged at most once per interval
    @Scheduled(fixedDelayString = "${trading.perps.funding-check-ms:30000}", initialDelayString = "${trading.perps.funding-check-ms:30000}")
    public void settleFunding() {
        settlement.settleCurrentFunding();
    }

    @Scheduled(fixedDelayString = "${trading.perps.liquidation-delay-ms:5000}",
            initialDelayString = "${trading.perps.liquidation-delay-ms:5000}")
    public void sweepLiquidations() {
        settlement.sweepLiquidations();
    }
}
