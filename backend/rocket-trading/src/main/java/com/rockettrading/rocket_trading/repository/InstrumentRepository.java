package com.rockettrading.rocket_trading.repository;

import com.rockettrading.rocket_trading.repository.model.FinancialInstrumentRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface InstrumentRepository {

    @Select("""
            select instrument_id, ticker_symbol, instrument_name, asset_class, base_currency, is_tradable as tradable
            from financial_instruments
            where upper(ticker_symbol) = upper(#{tickerSymbol})
            """)
    FinancialInstrumentRecord findByTicker(String tickerSymbol);

    @Insert("""
            insert into financial_instruments (ticker_symbol, instrument_name, asset_class, base_currency, is_tradable)
            values (#{tickerSymbol}, #{instrumentName}, #{assetClass}, #{baseCurrency}, #{tradable})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "instrumentId")
    int insert(FinancialInstrumentRecord instrument);
}
