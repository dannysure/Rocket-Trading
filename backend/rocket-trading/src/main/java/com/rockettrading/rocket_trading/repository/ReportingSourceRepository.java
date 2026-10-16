package com.rockettrading.rocket_trading.repository;

import com.rockettrading.rocket_trading.repository.model.ReportingOrderFactRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.Instant;
import java.util.List;

/**
 * The only reads reporting makes of the trading database: small, indexed lookups of what changed
 * since the last copy. Analyst queries never come here; they run on {@link ReportingStore}.
 */
@Mapper
public interface ReportingSourceRepository {

    /**
     * The highest audit id in the next batch after {@code afterId}, counting only rows older than
     * {@code settledBefore} so a transaction still committing is not skipped past. Null when nothing is new.
     */
    @Select("""
            select max(audit_id) from (
                select audit_id
                from audit_logs
                where audit_id > #{afterId}
                  and recorded_at <= #{settledBefore}
                order by audit_id
                limit #{batchSize}
            ) batch
            """)
    Long nextAuditWatermark(@Param("afterId") long afterId,
                            @Param("settledBefore") Instant settledBefore,
                            @Param("batchSize") int batchSize);

    /** Orders whose status, pricing or fill changed in the audit range (afterId, upToId]. */
    @Select("""
            select distinct entity_id
            from audit_logs
            where audit_id > #{afterId}
              and audit_id <= #{upToId}
              and entity_name = 'orders'
            """)
    List<Long> changedOrderIds(@Param("afterId") long afterId, @Param("upToId") long upToId);

    @Select("""
            <script>
            select o.order_id,
                   o.client_id,
                   cp.risk_profile,
                   fi.ticker_symbol as symbol,
                   fi.asset_class,
                   fi.market_segment,
                   o.order_side,
                   o.order_status,
                   o.requested_quantity,
                   o.submitted_at,
                   f.fill_id,
                   f.executed_at,
                   coalesce(f.settlement_amount, f.executed_quantity * f.executed_price) as settled_notional
            from orders o
            join client_profiles cp on cp.client_id = o.client_id
            join financial_instruments fi on fi.instrument_id = o.instrument_id
            left join fills f on f.order_id = o.order_id
            where o.order_id in
            <foreach item="id" collection="orderIds" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    List<ReportingOrderFactRecord> findOrderFacts(@Param("orderIds") List<Long> orderIds);
}
