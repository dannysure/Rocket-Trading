package com.rockettrading.rocket_trading.repository;

import com.rockettrading.rocket_trading.repository.model.MarketQuoteRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;

@Mapper
public interface MarketQuoteRepository {

    @Insert("""
            insert into market_quotes (instrument_id, bid_price, ask_price, quote_timestamp)
            values (#{instrumentId}, #{bidPrice}, #{askPrice}, #{quoteTimestamp})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "quoteId")
    int insert(MarketQuoteRecord quoteRecord);
}
