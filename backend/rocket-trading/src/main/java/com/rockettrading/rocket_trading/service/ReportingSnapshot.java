package com.rockettrading.rocket_trading.service;

import com.rockettrading.rocket_trading.repository.ReportingSourceRepository;
import com.rockettrading.rocket_trading.repository.ReportingStore;
import com.rockettrading.rocket_trading.repository.model.ReportingOrderFactRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Copies orders into the reporting database (BR-16). Each run reads only what changed since the last
 * one, found through the append-only audit trail, so the trading database does a few indexed lookups
 * instead of rebuilding a snapshot. A failed run changes nothing and the next one picks up where the
 * last good run stopped; trading never waits on it.
 */
@Component
public class ReportingSnapshot {
    private static final Logger log = LoggerFactory.getLogger(ReportingSnapshot.class);
    private static final int BATCH_SIZE = 5000;
    private static final int ORDER_CHUNK = 500;

    private final ReportingSourceRepository source;
    private final ReportingStore store;
    // Audit rows younger than this are left for the next run, so a transaction still committing is not skipped
    @Value("${reporting.sync.settle-seconds:5}")
    private long settleSeconds = 5;

    public ReportingSnapshot(ReportingSourceRepository source, ReportingStore store) {
        this.source = source;
        this.store = store;
    }

    @Scheduled(fixedDelayString = "${reporting.refresh-ms:30000}", initialDelay = 0)
    public void scheduledRefresh() {
        try {
            refresh();
        } catch (RuntimeException exception) {
            // Reporting going stale must never affect trading; analysts keep the last good copy
            log.warn("Reporting sync failed; serving the previous copy", exception);
        }
    }

    /** Brings the reporting database up to date with every order change that has settled. */
    public synchronized void refresh() {
        Instant settledBefore = Instant.now().minusSeconds(settleSeconds);
        long after = lastAuditId();
        while (true) {
            Long upTo = source.nextAuditWatermark(after, settledBefore, BATCH_SIZE);
            Set<Long> orderIds = new LinkedHashSet<>(openOrderIds());
            if (upTo != null) {
                orderIds.addAll(source.changedOrderIds(after, upTo));
            }
            List<ReportingOrderFactRecord> facts = new ArrayList<>();
            List<Long> ids = List.copyOf(orderIds);
            for (int i = 0; i < ids.size(); i += ORDER_CHUNK) {
                facts.addAll(source.findOrderFacts(ids.subList(i, Math.min(ids.size(), i + ORDER_CHUNK))));
            }
            long watermark = upTo == null ? after : upTo;
            save(facts, watermark);
            if (upTo == null) {
                return;
            }
            after = upTo;
        }
    }

    /** When the data analysts now see was last brought up to date; null before the first copy. */
    public Instant refreshedAt() {
        Timestamp syncedAt = store.jdbc().queryForObject(
                "select synced_at from sync_state where id = 1", Map.of(), Timestamp.class);
        return syncedAt == null ? null : syncedAt.toInstant();
    }

    private long lastAuditId() {
        Long id = store.jdbc().queryForObject("select last_audit_id from sync_state where id = 1", Map.of(), Long.class);
        return id == null ? 0 : id;
    }

    // In-flight orders are re-read every run until they settle, so a status change is never missed
    private List<Long> openOrderIds() {
        return store.jdbc().queryForList(
                "select order_id from order_facts where order_status in ('SUBMITTED', 'ACCEPTED')", Map.of(), Long.class);
    }

    private void save(List<ReportingOrderFactRecord> facts, long watermark) {
        store.transactions().executeWithoutResult(status -> {
            if (!facts.isEmpty()) {
                store.jdbc().batchUpdate("""
                        insert into order_facts (order_id, client_id, risk_profile, symbol, asset_class, market_segment,
                                                 order_side, order_status, requested_quantity, submitted_at, fill_id,
                                                 executed_at, settled_notional)
                        values (:orderId, :clientId, :riskProfile, :symbol, :assetClass, :marketSegment,
                                :orderSide, :orderStatus, :requestedQuantity, :submittedAt, :fillId,
                                :executedAt, :settledNotional)
                        on conflict (order_id) do update set
                            risk_profile = excluded.risk_profile,
                            order_status = excluded.order_status,
                            fill_id = excluded.fill_id,
                            executed_at = excluded.executed_at,
                            settled_notional = excluded.settled_notional
                        """, facts.stream().map(ReportingSnapshot::parameters).toArray(MapSqlParameterSource[]::new));
            }
            store.jdbc().update("update sync_state set last_audit_id = :watermark, synced_at = now() where id = 1",
                    Map.of("watermark", watermark));
        });
    }

    private static MapSqlParameterSource parameters(ReportingOrderFactRecord fact) {
        return new MapSqlParameterSource()
                .addValue("orderId", fact.getOrderId())
                .addValue("clientId", fact.getClientId())
                .addValue("riskProfile", fact.getRiskProfile())
                .addValue("symbol", fact.getSymbol())
                .addValue("assetClass", fact.getAssetClass())
                .addValue("marketSegment", fact.getMarketSegment())
                .addValue("orderSide", fact.getOrderSide())
                .addValue("orderStatus", fact.getOrderStatus())
                .addValue("requestedQuantity", fact.getRequestedQuantity())
                .addValue("submittedAt", utc(fact.getSubmittedAt()))
                .addValue("fillId", fact.getFillId())
                .addValue("executedAt", utc(fact.getExecutedAt()))
                .addValue("settledNotional", fact.getSettledNotional());
    }

    static OffsetDateTime utc(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }
}
