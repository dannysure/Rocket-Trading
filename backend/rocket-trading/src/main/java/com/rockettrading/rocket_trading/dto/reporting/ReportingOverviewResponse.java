package com.rockettrading.rocket_trading.dto.reporting;

import com.rockettrading.rocket_trading.repository.model.ReportingOverviewRecord;

import java.math.BigDecimal;
import java.time.Instant;

public record ReportingOverviewResponse(
        Instant from,
        Instant to,
        long totalOrders,
        long acceptedOrders,
        long filledOrders,
        long rejectedOrders,
        long totalFills,
        BigDecimal totalNotional,
        long activeClients,
        // When the reporting snapshot was taken; figures exclude anything after it
        Instant dataAsOf
) {
    public static ReportingOverviewResponse from(Instant from, Instant to, ReportingOverviewRecord record, Instant dataAsOf) {
        return new ReportingOverviewResponse(
                from,
                to,
                record.getTotalOrders(),
                record.getAcceptedOrders(),
                record.getFilledOrders(),
                record.getRejectedOrders(),
                record.getTotalFills(),
                record.getTotalNotional(),
                record.getActiveClients(),
                dataAsOf
        );
    }
}
