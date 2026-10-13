package com.rockettrading.rocket_trading.repository;

import com.rockettrading.rocket_trading.repository.model.TransactionRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface TransactionRepository {

    @Insert("""
            insert into transactions (account_id, instrument_id, fill_id, transaction_type, quantity, unit_price, net_amount, transaction_date)
            values (#{accountId}, #{instrumentId}, #{fillId}, #{transactionType}, #{quantity}, #{unitPrice}, #{netAmount}, #{transactionDate})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "transactionId")
    int insert(TransactionRecord transactionRecord);

    // Money the client has put in, net of withdrawals: the baseline for total return
    @Select("""
            select coalesce(sum(case transaction_type when 'DEPOSIT' then net_amount
                                                      when 'WITHDRAWAL' then -net_amount
                                                      else 0 end), 0)
            from transactions
            where account_id = #{accountId}
            """)
    BigDecimal sumNetDeposits(long accountId);

    @Select("""
            select t.transaction_id, t.account_id, t.instrument_id, t.fill_id, t.transaction_type,
                   t.quantity, t.unit_price, t.net_amount, t.transaction_date
            from transactions t
            join fills f on f.fill_id = t.fill_id
            where f.order_id = #{orderId}
            order by t.transaction_id
            """)
    List<TransactionRecord> findByOrderId(long orderId);
}
