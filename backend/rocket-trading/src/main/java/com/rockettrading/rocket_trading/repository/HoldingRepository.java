package com.rockettrading.rocket_trading.repository;

import com.rockettrading.rocket_trading.repository.model.HoldingRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface HoldingRepository {

    @Select("""
            select holding_id, account_id, instrument_id, quantity, average_cost, as_of_timestamp
            from account_holdings
            where account_id = #{accountId}
              and instrument_id = #{instrumentId}
            """)
    HoldingRecord findAccountHolding(@Param("accountId") long accountId, @Param("instrumentId") long instrumentId);

    @Insert("""
            insert into account_holdings (account_id, instrument_id, quantity, average_cost, as_of_timestamp)
            values (#{accountId}, #{instrumentId}, #{quantity}, #{averageCost}, current_timestamp)
            on conflict (account_id, instrument_id)
            do update set quantity = excluded.quantity, average_cost = excluded.average_cost, as_of_timestamp = current_timestamp
            """)
    int upsertAccountHolding(@Param("accountId") long accountId,
                             @Param("instrumentId") long instrumentId,
                             @Param("quantity") BigDecimal quantity,
                             @Param("averageCost") BigDecimal averageCost);

    @Insert("""
            insert into client_holdings (client_id, instrument_id, total_quantity, as_of_timestamp)
            values (#{clientId}, #{instrumentId}, #{quantity}, current_timestamp)
            on conflict (client_id, instrument_id)
            do update set total_quantity = excluded.total_quantity, as_of_timestamp = current_timestamp
            """)
    int upsertClientHolding(@Param("clientId") long clientId,
                            @Param("instrumentId") long instrumentId,
                            @Param("quantity") BigDecimal quantity);

    // Fully sold positions are kept for history but not shown as holdings
    @Select("""
            select ah.holding_id,
                   ah.account_id,
                   ah.instrument_id,
                   fi.ticker_symbol,
                   fi.instrument_name,
                   fi.asset_class,
                   fi.market_segment,
                   fi.base_currency,
                   ah.quantity,
                   ah.average_cost,
                   ah.as_of_timestamp
            from account_holdings ah
            join financial_instruments fi on fi.instrument_id = ah.instrument_id
            where ah.account_id = #{accountId}
              and ah.quantity > 0
            order by fi.ticker_symbol
            """)
    List<HoldingRecord> findOpenAccountHoldings(long accountId);
}
