package com.rockettrading.rocket_trading.repository;

import com.rockettrading.rocket_trading.repository.model.OrderRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface OrderRepository {

    @Insert("""
            insert into orders (client_id, account_id, instrument_id, order_side, order_type, requested_quantity, limit_price, order_status, rejection_reason)
            values (#{clientId}, #{accountId}, #{instrumentId}, #{orderSide}, #{orderType}, #{requestedQuantity}, #{limitPrice}, #{orderStatus}, #{rejectionReason})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "orderId")
    int insert(OrderRecord orderRecord);

    @Update("""
            update orders
            set order_status = #{orderStatus},
                rejection_reason = #{rejectionReason}
            where order_id = #{orderId}
            """)
    int updateStatus(OrderRecord orderRecord);

    @Select("""
            select o.order_id,
                   o.client_id,
                   o.account_id,
                   o.instrument_id,
                   fi.ticker_symbol as symbol,
                   o.order_side,
                   o.order_type,
                   o.requested_quantity,
                   o.limit_price,
                   o.order_status,
                   o.rejection_reason,
                   o.submitted_at
            from orders o
            join financial_instruments fi on fi.instrument_id = o.instrument_id
            where o.client_id = #{clientId}
            order by o.submitted_at desc
            """)
    List<OrderRecord> findByClientId(@Param("clientId") long clientId);

    @Select("""
            select o.order_id,
                   o.client_id,
                   o.account_id,
                   o.instrument_id,
                   fi.ticker_symbol as symbol,
                   o.order_side,
                   o.order_type,
                   o.requested_quantity,
                   o.limit_price,
                   o.order_status,
                   o.rejection_reason,
                   o.submitted_at
            from orders o
            join financial_instruments fi on fi.instrument_id = o.instrument_id
            where o.order_id = #{orderId}
              and o.client_id = #{clientId}
            """)
    OrderRecord findByIdAndClientId(@Param("orderId") long orderId, @Param("clientId") long clientId);
}
