package com.rockettrading.rocket_trading.repository;

import com.rockettrading.rocket_trading.repository.model.PerpPositionRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface PerpPositionRepository {

    @Insert("""
            insert into perp_positions (client_id, account_id, market_id, position_side, size, entry_price,
                                        isolated_margin, leverage, position_status)
            values (#{clientId}, #{accountId}, #{marketId}, #{positionSide}, #{size}, #{entryPrice},
                    #{isolatedMargin}, #{leverage}, 'OPEN')
            """)
    @Options(useGeneratedKeys = true, keyProperty = "positionId")
    int insert(PerpPositionRecord position);

    @Update("""
            update perp_positions
            set position_side = #{positionSide}, size = #{size}, entry_price = #{entryPrice},
                isolated_margin = #{isolatedMargin}, leverage = #{leverage}, position_status = #{positionStatus},
                realized_pnl = #{realizedPnl}, cumulative_funding = #{cumulativeFunding},
                updated_at = CURRENT_TIMESTAMP,
                closed_at = case when #{positionStatus} = 'OPEN' then null else CURRENT_TIMESTAMP end
            where position_id = #{positionId}
            """)
    int update(PerpPositionRecord position);

    // Callers lock the client's account row first, so position locks are always taken in account -> position order
    @Select("""
            select p.*, m.market_symbol from perp_positions p
            join perp_markets m on m.market_id = p.market_id
            where p.account_id = #{accountId} and p.market_id = #{marketId} and p.position_status = 'OPEN'
            for update of p
            """)
    PerpPositionRecord lockOpenPosition(@Param("accountId") long accountId, @Param("marketId") long marketId);

    @Select("""
            select p.*, m.market_symbol from perp_positions p
            join perp_markets m on m.market_id = p.market_id
            where p.position_id = #{positionId} and p.position_status = 'OPEN'
            for update of p
            """)
    PerpPositionRecord lockOpenPositionById(long positionId);

    @Select("""
            select p.*, m.market_symbol from perp_positions p
            join perp_markets m on m.market_id = p.market_id
            where p.client_id = #{clientId} and p.position_status = 'OPEN'
            order by p.position_id
            """)
    List<PerpPositionRecord> findOpenByClientId(long clientId);

    @Select("select position_id, client_id from perp_positions where position_status = 'OPEN' order by position_id")
    List<PerpPositionRecord> findOpenPositionOwners();
}
