package com.rockettrading.rocket_trading.repository;

import com.rockettrading.rocket_trading.repository.model.OrderRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;
import java.util.List;

@Mapper
public interface OrderRepository {

    String ORDER_VIEW = """
            select o.order_id, o.market, o.idempotency_key, o.request_fingerprint,
                   o.client_id,
                   o.account_id,
                   o.instrument_id,
                   fi.ticker_symbol as symbol,
                   fi.instrument_name,
                   fi.base_currency as instrument_currency,
                   o.order_side,
                   o.order_type,
                   o.requested_quantity,
                   o.limit_price,
                   o.order_status,
                   o.rejection_reason,
                   o.submitted_at,
                   o.next_execution_at,
                   f.executed_price,
                   coalesce(f.settlement_amount, f.executed_quantity * f.executed_price) as settlement_amount,
                   coalesce(f.settlement_currency, ca.currency) as settlement_currency,
                   f.executed_at
            from orders o
            join financial_instruments fi on fi.instrument_id = o.instrument_id
            join client_accounts ca on ca.account_id = o.account_id
            left join fills f on f.order_id = o.order_id
            """;

    @Insert("""
            insert into orders (client_id, account_id, instrument_id, order_side, order_type, requested_quantity, limit_price, order_status, rejection_reason, market, idempotency_key, request_fingerprint)
            values (#{clientId}, #{accountId}, #{instrumentId}, #{orderSide}, #{orderType}, #{requestedQuantity}, #{limitPrice}, #{orderStatus}, #{rejectionReason}, #{market}, #{idempotencyKey}, #{requestFingerprint})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "orderId")
    int insert(OrderRecord orderRecord);

    @Update("""
            update orders
            set order_status = #{orderStatus},
                rejection_reason = #{rejectionReason},
                next_execution_at = null
            where order_id = #{orderId}
            """)
    int updateStatus(OrderRecord orderRecord);

    @Update("update orders set next_execution_at = #{at} where order_id = #{orderId} and order_status = 'ACCEPTED'")
    int deferExecution(@Param("orderId") long orderId, @Param("at") Instant at);

    @Select(ORDER_VIEW + """
            where o.client_id = #{clientId}
            order by o.submitted_at desc, o.order_id desc
            """)
    List<OrderRecord> findByClientId(@Param("clientId") long clientId);

    @Select(ORDER_VIEW + """
            where o.order_id = #{orderId}
              and o.client_id = #{clientId}
            """)
    OrderRecord findByIdAndClientId(@Param("orderId") long orderId, @Param("clientId") long clientId);

    // Internal operations only: not scoped to a client
    @Select(ORDER_VIEW + "where o.order_id = #{orderId}")
    OrderRecord findById(@Param("orderId") long orderId);

    @Select("""
            select o.*, fi.ticker_symbol as symbol from orders o
            join financial_instruments fi on fi.instrument_id = o.instrument_id
            where o.client_id = #{clientId} and o.idempotency_key = #{key}
            """)
    OrderRecord findByIdempotencyKey(@Param("clientId") long clientId, @Param("key") String key);

    @Select("""
            select order_id from orders
            where order_status = 'ACCEPTED'
              and (next_execution_at is null or next_execution_at <= current_timestamp)
            order by next_execution_at nulls first, order_id
            limit 100
            """)
    List<Long> findPendingIds();

    @Select("""
            select o.*, fi.ticker_symbol as symbol from orders o
            join financial_instruments fi on fi.instrument_id = o.instrument_id
            where o.order_id = #{orderId} and o.order_status = 'ACCEPTED'
            for update of o skip locked
            """)
    OrderRecord lockAcceptedOrder(long orderId);
}
