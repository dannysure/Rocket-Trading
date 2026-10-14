package com.rockettrading.rocket_trading.service;

import com.rockettrading.rocket_trading.repository.ReportingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Keeps the reporting snapshot (reporting_order_facts) up to date. A CONCURRENT refresh lets
 * reporting reads continue during the rebuild and takes no locks that block order placement or settlement.
 */
@Component
public class ReportingSnapshot {
    private static final Logger log = LoggerFactory.getLogger(ReportingSnapshot.class);
    private final ReportingRepository reportingRepository;
    private final AtomicReference<Instant> refreshedAt = new AtomicReference<>();

    public ReportingSnapshot(ReportingRepository reportingRepository) {
        this.reportingRepository = reportingRepository;
    }

    @Scheduled(fixedDelayString = "${reporting.refresh-ms:30000}", initialDelay = 0)
    public void scheduledRefresh() {
        try {
            refresh();
        } catch (RuntimeException exception) {
            // Reporting going stale must never affect trading; the last good snapshot keeps serving
            log.warn("Reporting snapshot refresh failed; serving the previous snapshot", exception);
        }
    }

    public void refresh() {
        Instant startedAt = Instant.now();
        reportingRepository.refreshSnapshot();
        refreshedAt.set(startedAt);
    }

    /** When the data now being served was captured; null until the first refresh in this process. */
    public Instant refreshedAt() {
        return refreshedAt.get();
    }
}
