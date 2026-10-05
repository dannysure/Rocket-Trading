package com.rockettrading.rocket_trading.repository;

import com.rockettrading.rocket_trading.repository.model.ClientSegmentActivityRecord;
import com.rockettrading.rocket_trading.repository.model.InstrumentActivityRecord;
import com.rockettrading.rocket_trading.repository.model.ReportingOverviewRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.Instant;
import java.util.List;

@Mapper
public interface ReportingRepository {

    @Select("""
            select count(*) as totalOrders,
                   count(*) filter (where o.order_status = 'ACCEPTED') as acceptedOrders,
                   count(*) filter (where o.order_status = 'FILLED') as filledOrders,
                   count(*) filter (where o.order_status = 'REJECTED') as rejectedOrders,
                   count(f.fill_id) as totalFills,
                   coalesce(sum(f.executed_quantity * f.executed_price), 0) as totalNotional,
                   count(distinct o.client_id) as activeClients
            from orders o
            left join fills f on f.order_id = o.order_id
            where o.submitted_at >= #{from}
              and o.submitted_at <= #{to}
            """)
    ReportingOverviewRecord getOverview(@Param("from") Instant from, @Param("to") Instant to);

    @Select("""
            select fi.ticker_symbol as symbol,
                   fi.asset_class as assetClass,
                   count(o.order_id) as orderCount,
                   count(f.fill_id) as fillCount,
                   coalesce(sum(case when o.order_side = 'BUY' then o.requested_quantity else 0 end), 0) as buyQuantity,
                   coalesce(sum(case when o.order_side = 'SELL' then o.requested_quantity else 0 end), 0) as sellQuantity,
                   max(o.submitted_at) as lastSubmittedAt
            from orders o
            join financial_instruments fi on fi.instrument_id = o.instrument_id
            left join fills f on f.order_id = o.order_id
            where o.submitted_at >= #{from}
              and o.submitted_at <= #{to}
            group by fi.ticker_symbol, fi.asset_class
            order by orderCount desc, fi.ticker_symbol asc
            """)
    List<InstrumentActivityRecord> listInstrumentActivity(@Param("from") Instant from, @Param("to") Instant to);

    @Select("""
            select cp.risk_profile as riskProfile,
                   count(distinct o.client_id) as clientCount,
                   count(o.order_id) as orderCount,
                   coalesce(sum(f.executed_quantity * f.executed_price), 0) as totalNotional
            from orders o
            join client_profiles cp on cp.client_id = o.client_id
            left join fills f on f.order_id = o.order_id
            where o.submitted_at >= #{from}
              and o.submitted_at <= #{to}
            group by cp.risk_profile
            order by orderCount desc, cp.risk_profile asc
            """)
    List<ClientSegmentActivityRecord> listClientSegmentActivity(@Param("from") Instant from, @Param("to") Instant to);
}
