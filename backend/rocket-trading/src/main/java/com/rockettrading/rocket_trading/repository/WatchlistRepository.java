package com.rockettrading.rocket_trading.repository;

import com.rockettrading.rocket_trading.repository.model.WatchlistEntryRecord;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface WatchlistRepository {

    @Select("""
            select fi.instrument_id, fi.ticker_symbol, fi.instrument_name, fi.asset_class, fi.base_currency,
                   fi.is_tradable as tradable, fi.market_segment, fi.listing_exchange, w.added_at
            from client_watchlist w
            join financial_instruments fi on fi.instrument_id = w.instrument_id
            where w.client_id = #{clientId}
            order by w.added_at desc
            """)
    List<WatchlistEntryRecord> findByClientId(long clientId);

    @Insert("""
            insert into client_watchlist (client_id, instrument_id)
            values (#{clientId}, #{instrumentId})
            on conflict do nothing
            """)
    int add(@Param("clientId") long clientId, @Param("instrumentId") long instrumentId);

    @Delete("delete from client_watchlist where client_id = #{clientId} and instrument_id = #{instrumentId}")
    int remove(@Param("clientId") long clientId, @Param("instrumentId") long instrumentId);
}
