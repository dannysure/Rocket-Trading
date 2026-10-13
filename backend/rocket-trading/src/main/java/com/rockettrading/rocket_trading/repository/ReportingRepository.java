package com.rockettrading.rocket_trading.repository;

import com.rockettrading.rocket_trading.repository.model.ClientSegmentActivityRecord;
import com.rockettrading.rocket_trading.repository.model.InstrumentActivityRecord;
import com.rockettrading.rocket_trading.repository.model.MarketSegmentActivityRecord;
import com.rockettrading.rocket_trading.repository.model.ReportingOverviewRecord;
import com.rockettrading.rocket_trading.repository.model.VolumeBucketRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;
import java.util.List;

/**
 * Reads only the reporting_order_facts snapshot (V9), never the live order tables.
 * Notional is in the settlement (account) currency.
 */
@Mapper
public interface ReportingRepository {

    @Update("refresh materialized view concurrently reporting_order_facts")
    void refreshSnapshot();

    @Select("""
            select count(*) as totalOrders,
                   count(*) filter (where order_status = 'ACCEPTED') as acceptedOrders,
                   count(*) filter (where order_status = 'FILLED') as filledOrders,
                   count(*) filter (where order_status = 'REJECTED') as rejectedOrders,
                   count(fill_id) as totalFills,
                   coalesce(sum(settled_notional), 0) as totalNotional,
                   count(distinct client_id) as activeClients
            from reporting_order_facts
            where submitted_at >= #{from}
              and submitted_at <= #{to}
            """)
    ReportingOverviewRecord getOverview(@Param("from") Instant from, @Param("to") Instant to);

    @Select("""
            select symbol,
                   asset_class as assetClass,
                   market_segment as marketSegment,
                   count(*) as orderCount,
                   count(fill_id) as fillCount,
                   coalesce(sum(case when order_side = 'BUY' then requested_quantity else 0 end), 0) as buyQuantity,
                   coalesce(sum(case when order_side = 'SELL' then requested_quantity else 0 end), 0) as sellQuantity,
                   coalesce(sum(settled_notional), 0) as totalNotional,
                   max(submitted_at) as lastSubmittedAt
            from reporting_order_facts
            where submitted_at >= #{from}
              and submitted_at <= #{to}
            group by symbol, asset_class, market_segment
            order by orderCount desc, totalNotional desc, symbol asc
            """)
    List<InstrumentActivityRecord> listInstrumentActivity(@Param("from") Instant from, @Param("to") Instant to);

    @Select("""
            select risk_profile as riskProfile,
                   count(distinct client_id) as clientCount,
                   count(*) as orderCount,
                   coalesce(sum(settled_notional), 0) as totalNotional
            from reporting_order_facts
            where submitted_at >= #{from}
              and submitted_at <= #{to}
            group by risk_profile
            order by orderCount desc, risk_profile asc
            """)
    List<ClientSegmentActivityRecord> listClientSegmentActivity(@Param("from") Instant from, @Param("to") Instant to);

    @Select("""
            select market_segment as marketSegment,
                   count(*) as orderCount,
                   count(fill_id) as fillCount,
                   coalesce(sum(settled_notional), 0) as totalNotional
            from reporting_order_facts
            where submitted_at >= #{from}
              and submitted_at <= #{to}
            group by market_segment
            order by totalNotional desc, orderCount desc
            """)
    List<MarketSegmentActivityRecord> listMarketSegmentActivity(@Param("from") Instant from, @Param("to") Instant to);

    // bucket is validated to 'day' or 'week' by the service before it reaches SQL
    @Select("""
            select date_trunc(#{bucket}, submitted_at, 'UTC') as periodStart,
                   count(*) as orderCount,
                   count(fill_id) as fillCount,
                   coalesce(sum(settled_notional), 0) as totalNotional
            from reporting_order_facts
            where submitted_at >= #{from}
              and submitted_at <= #{to}
            group by 1
            order by 1
            """)
    List<VolumeBucketRecord> listVolume(@Param("from") Instant from, @Param("to") Instant to, @Param("bucket") String bucket);
}
