package com.rockettrading.rocket_trading.repository;

import com.rockettrading.rocket_trading.repository.model.PerpMarketRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface PerpMarketRepository {

    @Select("""
            select market_id, market_symbol, base_asset, underlying_symbol, underlying_market,
                   quote_currency, max_leverage, taker_fee_rate
            from perp_markets
            where is_active
            order by market_id
            """)
    List<PerpMarketRecord> findActive();

    @Select("""
            select market_id, market_symbol, base_asset, underlying_symbol, underlying_market,
                   quote_currency, max_leverage, taker_fee_rate
            from perp_markets
            where market_symbol = #{symbol} and is_active
            """)
    PerpMarketRecord findActiveBySymbol(String symbol);

    // Includes delisted markets so positions opened before delisting still resolve
    @Select("""
            select market_id, market_symbol, base_asset, underlying_symbol, underlying_market,
                   quote_currency, max_leverage, taker_fee_rate
            from perp_markets
            order by market_id
            """)
    List<PerpMarketRecord> findAll();

    @Select("""
            select market_id, market_symbol, base_asset, underlying_symbol, underlying_market,
                   quote_currency, max_leverage, taker_fee_rate
            from perp_markets
            where market_id = #{marketId}
            """)
    PerpMarketRecord findById(long marketId);

    @Select("""
            select coalesce(sum(size), 0) from perp_positions
            where market_id = #{marketId} and position_status = 'OPEN'
            """)
    BigDecimal openInterest(long marketId);
}
