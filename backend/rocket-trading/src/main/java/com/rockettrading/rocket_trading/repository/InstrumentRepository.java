package com.rockettrading.rocket_trading.repository;

import com.rockettrading.rocket_trading.repository.model.FinancialInstrumentRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface InstrumentRepository {

    @Select("""
            select instrument_id, ticker_symbol, instrument_name, asset_class, base_currency, is_tradable as tradable,
                   market_segment, listing_exchange
            from financial_instruments
            where upper(ticker_symbol) = upper(#{tickerSymbol})
            """)
    FinancialInstrumentRecord findByTicker(String tickerSymbol);

    @Insert("""
            insert into financial_instruments (ticker_symbol, instrument_name, asset_class, base_currency, is_tradable, market_segment, listing_exchange)
            values (#{tickerSymbol}, #{instrumentName}, #{assetClass}, #{baseCurrency}, #{tradable}, #{marketSegment}, #{listingExchange})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "instrumentId")
    int insert(FinancialInstrumentRecord instrument);

    // Concurrent first orders for the same new symbol must not fail on the unique ticker constraint
    @Insert("""
            insert into financial_instruments (ticker_symbol, instrument_name, asset_class, base_currency, is_tradable, market_segment, listing_exchange)
            values (#{tickerSymbol}, #{instrumentName}, #{assetClass}, #{baseCurrency}, #{tradable}, #{marketSegment}, #{listingExchange})
            on conflict (ticker_symbol) do nothing
            """)
    int insertIfAbsent(FinancialInstrumentRecord instrument);

    @Select("""
            select instrument_id, ticker_symbol, instrument_name, asset_class, base_currency, is_tradable as tradable,
                   market_segment, listing_exchange
            from financial_instruments
            where is_tradable = true
            order by case market_segment when 'US' then 1 when 'UK' then 2 when 'IN' then 3 when 'FX' then 4 else 5 end,
                     ticker_symbol
            """)
    List<FinancialInstrumentRecord> findSupportedInstruments();

    @Select("""
            select instrument_id, ticker_symbol, instrument_name, asset_class, base_currency, is_tradable as tradable,
                   market_segment, listing_exchange
            from financial_instruments
            order by ticker_symbol
            """)
    List<FinancialInstrumentRecord> findAll();

    // Everything describing the instrument comes from the quote provider; only the platform's grouping is kept
    @Update("""
            update financial_instruments
            set instrument_name = #{instrumentName},
                asset_class = #{assetClass},
                base_currency = #{baseCurrency},
                is_tradable = #{tradable},
                listing_exchange = #{listingExchange},
                market_segment = #{marketSegment}
            where instrument_id = #{instrumentId}
            """)
    int updateFromProvider(FinancialInstrumentRecord instrument);

    @Update("update financial_instruments set is_tradable = false where instrument_id = #{instrumentId}")
    int markNotTradable(long instrumentId);
}
