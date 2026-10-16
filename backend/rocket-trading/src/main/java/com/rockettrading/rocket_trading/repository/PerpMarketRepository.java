package com.rockettrading.rocket_trading.repository;

import com.rockettrading.rocket_trading.repository.model.PerpMarketRecord;
import com.rockettrading.rocket_trading.repository.model.PerpOpenInterestRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PerpMarketRepository {

    @Select("""
            select market_id, market_symbol, base_asset, underlying_symbol, underlying_market,
                   quote_currency, max_leverage, taker_fee_rate, skew_scale
            from perp_markets
            where is_active
            order by market_id
            """)
    List<PerpMarketRecord> findActive();

    @Select("""
            select market_id, market_symbol, base_asset, underlying_symbol, underlying_market,
                   quote_currency, max_leverage, taker_fee_rate, skew_scale
            from perp_markets
            where market_symbol = #{symbol} and is_active
            """)
    PerpMarketRecord findActiveBySymbol(String symbol);

    // Includes delisted markets so positions opened before delisting still resolve
    @Select("""
            select market_id, market_symbol, base_asset, underlying_symbol, underlying_market,
                   quote_currency, max_leverage, taker_fee_rate, skew_scale
            from perp_markets
            order by market_id
            """)
    List<PerpMarketRecord> findAll();

    @Select("""
            select market_id, market_symbol, base_asset, underlying_symbol, underlying_market,
                   quote_currency, max_leverage, taker_fee_rate, skew_scale
            from perp_markets
            where market_id = #{marketId}
            """)
    PerpMarketRecord findById(long marketId);

    /** Open long and short size per market; markets without open positions are absent. */
    @Select("""
            select market_id,
                   coalesce(sum(size) filter (where position_side = 'LONG'), 0) as long_size,
                   coalesce(sum(size) filter (where position_side = 'SHORT'), 0) as short_size
            from perp_positions
            where position_status = 'OPEN'
            group by market_id
            """)
    List<PerpOpenInterestRecord> openInterest();

    /** Serializes fills in one market, so each order is priced against the skew left by the one before it. */
    @Select("select market_id from perp_markets where market_id = #{marketId} for update")
    Long lockMarket(long marketId);
}
