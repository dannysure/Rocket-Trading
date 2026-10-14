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
            insert into fills (order_id, executed_quantity, executed_price, quote_id, executed_at,
                               instrument_currency, settlement_currency, fx_rate, settlement_amount)
            values (#{orderId}, #{executedQuantity}, #{executedPrice}, #{quoteId}, #{executedAt},
                    #{instrumentCurrency}, #{settlementCurrency}, #{fxRate}, #{settlementAmount})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "fillId")
    int insert(FillRecord fillRecord);

    @Select("""
            select f.fill_id, f.order_id, f.executed_quantity, f.executed_price, f.quote_id, f.executed_at,
                   coalesce(f.instrument_currency, fi.base_currency) as instrument_currency,
                   coalesce(f.settlement_currency, ca.currency) as settlement_currency,
                   coalesce(f.fx_rate, 1) as fx_rate,
                   coalesce(f.settlement_amount, f.executed_quantity * f.executed_price) as settlement_amount
            from fills f
            join orders o on o.order_id = f.order_id
            join financial_instruments fi on fi.instrument_id = o.instrument_id
            join client_accounts ca on ca.account_id = o.account_id
            where f.order_id = #{orderId}
            order by f.executed_at desc
            """)
    List<FillRecord> findByOrderId(long orderId);
}
