package com.rockettrading.rocket_trading.service;

import com.rockettrading.rocket_trading.dto.reporting.ClientSegmentActivityResponse;
import com.rockettrading.rocket_trading.dto.reporting.InstrumentActivityResponse;
import com.rockettrading.rocket_trading.dto.reporting.MarketSegmentActivityResponse;
import com.rockettrading.rocket_trading.dto.reporting.ReportingOverviewResponse;
import com.rockettrading.rocket_trading.dto.reporting.VolumeBucketResponse;
import com.rockettrading.rocket_trading.repository.ReportingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class ReportingService {
    private final ReportingRepository reportingRepository;
    private final ReportingSnapshot reportingSnapshot;

    public ReportingService(ReportingRepository reportingRepository, ReportingSnapshot reportingSnapshot) {
        this.reportingRepository = reportingRepository;
        this.reportingSnapshot = reportingSnapshot;
    }

    public ReportingOverviewResponse getOverview(Instant from, Instant to) {
        TimeWindow window = normalizeWindow(from, to);
        return ReportingOverviewResponse.from(
                window.from(),
                window.to(),
                reportingRepository.getOverview(window.from(), window.to()),
                reportingSnapshot.refreshedAt()
        );
    }

    public List<InstrumentActivityResponse> listInstrumentActivity(Instant from, Instant to) {
        TimeWindow window = normalizeWindow(from, to);
        return reportingRepository.listInstrumentActivity(window.from(), window.to()).stream()
                .map(InstrumentActivityResponse::from)
                .toList();
    }

    public List<ClientSegmentActivityResponse> listClientSegmentActivity(Instant from, Instant to) {
        TimeWindow window = normalizeWindow(from, to);
        return reportingRepository.listClientSegmentActivity(window.from(), window.to()).stream()
                .map(ClientSegmentActivityResponse::from)
                .toList();
    }

    public List<MarketSegmentActivityResponse> listMarketSegmentActivity(Instant from, Instant to) {
        TimeWindow window = normalizeWindow(from, to);
        return reportingRepository.listMarketSegmentActivity(window.from(), window.to()).stream()
                .map(MarketSegmentActivityResponse::from)
                .toList();
    }

    public List<VolumeBucketResponse> listVolume(Instant from, Instant to, String bucket) {
        if (!"day".equals(bucket) && !"week".equals(bucket)) {
            throw new IllegalArgumentException("bucket must be day or week");
        }
        TimeWindow window = normalizeWindow(from, to);
        return reportingRepository.listVolume(window.from(), window.to(), bucket).stream()
                .map(VolumeBucketResponse::from)
                .toList();
    }

    private TimeWindow normalizeWindow(Instant from, Instant to) {
        Instant resolvedTo = to != null ? to : Instant.now();
        Instant resolvedFrom = from != null ? from : resolvedTo.minus(30, ChronoUnit.DAYS);
        if (resolvedFrom.isAfter(resolvedTo)) {
            throw new IllegalArgumentException("from must be before or equal to to");
        }
        return new TimeWindow(resolvedFrom, resolvedTo);
    }

    private record TimeWindow(Instant from, Instant to) {
    }
}
