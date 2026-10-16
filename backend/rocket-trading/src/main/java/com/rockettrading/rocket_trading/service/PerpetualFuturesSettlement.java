package com.rockettrading.rocket_trading.service;

import com.rockettrading.rocket_trading.repository.model.PerpPositionRecord;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Sweeps open positions one transaction at a time, so a failed quote or rollback for one position
 * never blocks funding or liquidation of the others.
 */
@Component
@RequiredArgsConstructor
public class PerpetualFuturesSettlement {
    private static final Logger log = LoggerFactory.getLogger(PerpetualFuturesSettlement.class);
    private final PerpetualFuturesService service;

    /** Charges the interval in progress to every position held over its start that has not paid it yet. */
    public int settleCurrentFunding() {
        return settleFunding(service.currentFundingTime());
    }

    /** Returns how many positions were charged for this funding time. */
    public int settleFunding(Instant fundingTime) {
        int charged = 0;
        for (PerpPositionRecord owner : service.positionsDueFunding(fundingTime)) {
            try {
                if (service.settleFunding(owner.getPositionId(), owner.getClientId(), fundingTime)) charged++;
            } catch (RuntimeException exception) {
                log.warn("Funding at {} rolled back for perp position {}; it is retried on the next check: {}",
                        fundingTime, owner.getPositionId(), exception.getMessage());
            }
        }
        return charged;
    }

    /** Returns how many positions were liquidated. */
    public int sweepLiquidations() {
        int liquidated = 0;
        for (PerpPositionRecord owner : service.openPositionOwners()) {
            try {
                if (service.liquidateIfNeeded(owner.getPositionId(), owner.getClientId())) liquidated++;
            } catch (RuntimeException exception) {
                log.error("Liquidation check rolled back for perp position {}. It is retried on the next sweep.",
                        owner.getPositionId(), exception);
            }
        }
        return liquidated;
    }
}
