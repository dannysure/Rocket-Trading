package com.rockettrading.rocket_trading.repository;

import com.rockettrading.rocket_trading.repository.model.PerpFundingRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PerpFundingRepository {

    /** Returns 0 when this position was already charged for this funding time. */
    @Insert("""
            insert into perp_funding_payments (position_id, client_id, market_id, funding_time, position_side,
                                               size, mark_price, funding_rate, payment)
            values (#{positionId}, #{clientId}, #{marketId}, #{fundingTime}, #{positionSide},
                    #{size}, #{markPrice}, #{fundingRate}, #{payment})
            on conflict (position_id, funding_time) do nothing
            """)
    int insertIfAbsent(PerpFundingRecord funding);

    @Select("""
            select f.*, m.market_symbol from perp_funding_payments f
            join perp_markets m on m.market_id = f.market_id
            where f.client_id = #{clientId}
            order by f.funding_time desc, f.funding_id desc
            limit 200
            """)
    List<PerpFundingRecord> findByClientId(long clientId);
}
