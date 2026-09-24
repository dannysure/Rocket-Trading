package com.rockettrading.rocket_trading.repository;

import com.rockettrading.rocket_trading.repository.model.TransactionRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;

@Mapper
public interface TransactionRepository {

    @Insert("""
            insert into transactions (account_id, instrument_id, fill_id, transaction_type, quantity, unit_price, net_amount, transaction_date)
            values (#{accountId}, #{instrumentId}, #{fillId}, #{transactionType}, #{quantity}, #{unitPrice}, #{netAmount}, #{transactionDate})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "transactionId")
    int insert(TransactionRecord transactionRecord);
}
