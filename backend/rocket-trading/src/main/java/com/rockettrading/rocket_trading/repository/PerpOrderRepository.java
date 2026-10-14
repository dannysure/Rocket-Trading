package com.rockettrading.rocket_trading.repository;

import com.rockettrading.rocket_trading.repository.model.PerpOrderRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PerpOrderRepository {

    @Insert("""
            insert into perp_orders (client_id, account_id, market_id, position_id, order_side, order_kind,
                                     requested_size, filled_size, leverage, reduce_only, order_status, fill_price,
                                     fee, realized_pnl, rejection_reason, idempotency_key, request_fingerprint)
            values (#{clientId}, #{accountId}, #{marketId}, #{positionId}, #{orderSide}, #{orderKind},
                    #{requestedSize}, #{filledSize}, #{leverage}, #{reduceOnly}, #{orderStatus}, #{fillPrice},
                    #{fee}, #{realizedPnl}, #{rejectionReason}, #{idempotencyKey}, #{requestFingerprint})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "perpOrderId", keyColumn = "perp_order_id")
    int insert(PerpOrderRecord order);

    @Select("""
            select o.*, m.market_symbol from perp_orders o
            join perp_markets m on m.market_id = o.market_id
            where o.client_id = #{clientId} and o.idempotency_key = #{key}
            """)
    PerpOrderRecord findByIdempotencyKey(@Param("clientId") long clientId, @Param("key") String key);

    @Select("""
            select o.*, m.market_symbol from perp_orders o
            join perp_markets m on m.market_id = o.market_id
            where o.perp_order_id = #{orderId}
            """)
    PerpOrderRecord findById(long orderId);

    @Select("""
            select o.*, m.market_symbol from perp_orders o
            join perp_markets m on m.market_id = o.market_id
            where o.client_id = #{clientId}
            order by o.submitted_at desc, o.perp_order_id desc
            limit 200
            """)
    List<PerpOrderRecord> findByClientId(long clientId);
}
