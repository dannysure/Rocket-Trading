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
            select holding_id, account_id, instrument_id, quantity, as_of_timestamp
            from account_holdings
            where account_id = #{accountId}
              and instrument_id = #{instrumentId}
            """)
    HoldingRecord findAccountHolding(@Param("accountId") long accountId, @Param("instrumentId") long instrumentId);

    @Select("""
            select client_holding_id as holding_id, client_id, instrument_id, total_quantity as quantity, as_of_timestamp
            from client_holdings
            where client_id = #{clientId}
              and instrument_id = #{instrumentId}
            """)
    HoldingRecord findClientHolding(@Param("clientId") long clientId, @Param("instrumentId") long instrumentId);

    @Insert("""
            insert into account_holdings (account_id, instrument_id, quantity, as_of_timestamp)
            values (#{accountId}, #{instrumentId}, #{quantity}, current_timestamp)
            on conflict (account_id, instrument_id)
            do update set quantity = excluded.quantity, as_of_timestamp = current_timestamp
            """)
    int upsertAccountHolding(@Param("accountId") long accountId,
                             @Param("instrumentId") long instrumentId,
                             @Param("quantity") BigDecimal quantity);

    @Insert("""
            insert into client_holdings (client_id, instrument_id, total_quantity, as_of_timestamp)
            values (#{clientId}, #{instrumentId}, #{quantity}, current_timestamp)
            on conflict (client_id, instrument_id)
            do update set total_quantity = excluded.total_quantity, as_of_timestamp = current_timestamp
            """)
    int upsertClientHolding(@Param("clientId") long clientId,
                            @Param("instrumentId") long instrumentId,
                            @Param("quantity") BigDecimal quantity);

    @Select("""
            select ch.client_holding_id as holding_id,
                   ch.client_id,
                   ch.instrument_id,
                   fi.ticker_symbol,
                   ch.total_quantity as quantity,
                   ch.as_of_timestamp
            from client_holdings ch
            join financial_instruments fi on fi.instrument_id = ch.instrument_id
            where ch.client_id = #{clientId}
            order by fi.ticker_symbol
            """)
    List<HoldingRecord> findClientHoldings(long clientId);
}
