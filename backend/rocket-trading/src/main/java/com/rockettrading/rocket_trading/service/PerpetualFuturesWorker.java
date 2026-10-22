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

    /** Funding hits accounts on the hour (UTC) and nowhere else in it. */
    public static final String FUNDING_SCHEDULE = "*/15 0-4 * * * *";

    // Runs at hh:00:00, then every 15 seconds until hh:04:45 to retry any charge that failed (no price, a restart).
    // Each position is charged at most once per hour, so the retries never charge twice.
    @Scheduled(cron = FUNDING_SCHEDULE, zone = "UTC")
    public void settleFunding() {
        settlement.settleCurrentFunding();
    }

    @Scheduled(fixedDelayString = "${trading.perps.liquidation-delay-ms:5000}",
            initialDelayString = "${trading.perps.liquidation-delay-ms:5000}")
    public void sweepLiquidations() {
        settlement.sweepLiquidations();
    }
}
