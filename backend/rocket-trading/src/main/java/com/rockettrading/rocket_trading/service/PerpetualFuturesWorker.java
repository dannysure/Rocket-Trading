package com.rockettrading.rocket_trading.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "trading.worker.enabled", havingValue = "true", matchIfMissing = true)
public class PerpetualFuturesWorker {
    private final PerpetualFuturesSettlement settlement;

    // UTC so the charged interval matches the nextFundingAt shown to clients
    @Scheduled(cron = "0 0 * * * *", zone = "UTC")
    public void settleHourlyFunding() {
        settlement.settleFunding(Instant.now().truncatedTo(ChronoUnit.HOURS));
    }

    @Scheduled(fixedDelayString = "${trading.perps.liquidation-delay-ms:5000}",
            initialDelayString = "${trading.perps.liquidation-delay-ms:5000}")
    public void sweepLiquidations() {
        settlement.sweepLiquidations();
    }
}
