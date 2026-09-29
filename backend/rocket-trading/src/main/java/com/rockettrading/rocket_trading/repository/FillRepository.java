package com.rockettrading.rocket_trading.repository;

import com.rockettrading.rocket_trading.repository.model.FillRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface FillRepository {

    @Insert("""
            insert into fills (order_id, executed_quantity, executed_price, quote_id, executed_at)
            values (#{orderId}, #{executedQuantity}, #{executedPrice}, #{quoteId}, #{executedAt})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "fillId")
    int insert(FillRecord fillRecord);

    @Select("""
            select fill_id, order_id, executed_quantity, executed_price, quote_id, executed_at
            from fills
            where order_id = #{orderId}
            order by executed_at desc
            """)
    List<FillRecord> findByOrderId(long orderId);
}
