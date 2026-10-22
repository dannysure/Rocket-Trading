package com.rockettrading.rocket_trading.repository;

import com.rockettrading.rocket_trading.repository.model.ClientSegmentActivityRecord;
import com.rockettrading.rocket_trading.repository.model.InstrumentActivityRecord;
import com.rockettrading.rocket_trading.repository.model.MarketSegmentActivityRecord;
import com.rockettrading.rocket_trading.repository.model.ReportingOverviewRecord;
import com.rockettrading.rocket_trading.repository.model.VolumeBucketRecord;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Analyst queries (BR-16/17). They run only on the reporting database through {@link ReportingStore},
 * never on the trading database. Notional is in the settlement (account) currency.
 */
@Repository
public class ReportingRepository {
    private final ReportingStore store;

    public ReportingRepository(ReportingStore store) {
        this.store = store;
    }

    public ReportingOverviewRecord getOverview(Instant from, Instant to) {
        return store.jdbc().queryForObject("""
                select count(*) as total_orders,
                       count(*) filter (where order_status = 'ACCEPTED') as accepted_orders,
                       count(*) filter (where order_status = 'FILLED') as filled_orders,
                       count(*) filter (where order_status = 'REJECTED') as rejected_orders,
                       count(fill_id) as total_fills,
                       coalesce(sum(settled_notional), 0) as total_notional,
                       count(distinct client_id) as active_clients
                from order_facts
                where submitted_at >= :from
                  and submitted_at <= :to
                """, window(from, to), (rs, row) -> {
            ReportingOverviewRecord record = new ReportingOverviewRecord();
            record.setTotalOrders(rs.getLong("total_orders"));
            record.setAcceptedOrders(rs.getLong("accepted_orders"));
            record.setFilledOrders(rs.getLong("filled_orders"));
            record.setRejectedOrders(rs.getLong("rejected_orders"));
            record.setTotalFills(rs.getLong("total_fills"));
            record.setTotalNotional(rs.getBigDecimal("total_notional"));
            record.setActiveClients(rs.getLong("active_clients"));
            return record;
        });
    }

    public List<InstrumentActivityRecord> listInstrumentActivity(Instant from, Instant to) {
        return store.jdbc().query("""
                select symbol,
                       asset_class,
                       market_segment,
                       count(*) as order_count,
                       count(fill_id) as fill_count,
                       coalesce(sum(case when order_side = 'BUY' then requested_quantity else 0 end), 0) as buy_quantity,
                       coalesce(sum(case when order_side = 'SELL' then requested_quantity else 0 end), 0) as sell_quantity,
                       coalesce(sum(settled_notional), 0) as total_notional,
                       max(submitted_at) as last_submitted_at
                from order_facts
                where submitted_at >= :from
                  and submitted_at <= :to
                group by symbol, asset_class, market_segment
                order by order_count desc, total_notional desc, symbol asc
                """, window(from, to), (rs, row) -> {
            InstrumentActivityRecord record = new InstrumentActivityRecord();
            record.setSymbol(rs.getString("symbol"));
            record.setAssetClass(rs.getString("asset_class"));
            record.setMarketSegment(rs.getString("market_segment"));
            record.setOrderCount(rs.getLong("order_count"));
            record.setFillCount(rs.getLong("fill_count"));
            record.setBuyQuantity(rs.getBigDecimal("buy_quantity"));
            record.setSellQuantity(rs.getBigDecimal("sell_quantity"));
            record.setTotalNotional(rs.getBigDecimal("total_notional"));
            record.setLastSubmittedAt(instant(rs, "last_submitted_at"));
            return record;
        });
    }

    public List<ClientSegmentActivityRecord> listClientSegmentActivity(Instant from, Instant to) {
        return store.jdbc().query("""
                select risk_profile,
                       count(distinct client_id) as client_count,
                       count(*) as order_count,
                       coalesce(sum(settled_notional), 0) as total_notional
                from order_facts
                where submitted_at >= :from
                  and submitted_at <= :to
                group by risk_profile
                order by order_count desc, risk_profile asc
                """, window(from, to), (rs, row) -> {
            ClientSegmentActivityRecord record = new ClientSegmentActivityRecord();
            record.setRiskProfile(rs.getString("risk_profile"));
            record.setClientCount(rs.getLong("client_count"));
            record.setOrderCount(rs.getLong("order_count"));
            record.setTotalNotional(rs.getBigDecimal("total_notional"));
            return record;
        });
    }

    public List<MarketSegmentActivityRecord> listMarketSegmentActivity(Instant from, Instant to) {
        return store.jdbc().query("""
                select market_segment,
                       count(*) as order_count,
                       count(fill_id) as fill_count,
                       coalesce(sum(settled_notional), 0) as total_notional
                from order_facts
                where submitted_at >= :from
                  and submitted_at <= :to
                group by market_segment
                order by total_notional desc, order_count desc
                """, window(from, to), (rs, row) -> {
            MarketSegmentActivityRecord record = new MarketSegmentActivityRecord();
            record.setMarketSegment(rs.getString("market_segment"));
            record.setOrderCount(rs.getLong("order_count"));
            record.setFillCount(rs.getLong("fill_count"));
            record.setTotalNotional(rs.getBigDecimal("total_notional"));
            return record;
        });
    }

    // bucket is validated to 'day' or 'week' by the service before it reaches SQL
    public List<VolumeBucketRecord> listVolume(Instant from, Instant to, String bucket) {
        return store.jdbc().query("""
                select date_trunc(:bucket, submitted_at, 'UTC') as period_start,
                       count(*) as order_count,
                       count(fill_id) as fill_count,
                       coalesce(sum(settled_notional), 0) as total_notional
                from order_facts
                where submitted_at >= :from
                  and submitted_at <= :to
                group by 1
                order by 1
                """, window(from, to).addValue("bucket", bucket), (rs, row) -> {
            VolumeBucketRecord record = new VolumeBucketRecord();
            record.setPeriodStart(instant(rs, "period_start"));
            record.setOrderCount(rs.getLong("order_count"));
            record.setFillCount(rs.getLong("fill_count"));
            record.setTotalNotional(rs.getBigDecimal("total_notional"));
            return record;
        });
    }

    private static MapSqlParameterSource window(Instant from, Instant to) {
        return new MapSqlParameterSource()
                .addValue("from", from.atOffset(ZoneOffset.UTC))
                .addValue("to", to.atOffset(ZoneOffset.UTC));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
