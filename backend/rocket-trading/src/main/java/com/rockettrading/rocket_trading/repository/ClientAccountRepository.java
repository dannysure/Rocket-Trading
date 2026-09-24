package com.rockettrading.rocket_trading.repository;

import com.rockettrading.rocket_trading.repository.model.ClientAccountRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

@Mapper
public interface ClientAccountRepository {

    @Insert("""
            insert into client_accounts (client_id, account_type, cash_balance, currency, opened_date)
            values (#{clientId}, #{accountType}, #{cashBalance}, #{currency}, #{openedDate})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "accountId")
    int insert(ClientAccountRecord account);

    @Select("""
            select account_id, client_id, account_type, cash_balance, currency, opened_date
            from client_accounts
            where client_id = #{clientId}
              and account_type = 'DIRECT_TRADING'
            order by account_id
            limit 1
            """)
    ClientAccountRecord findDirectTradingAccountByClientId(long clientId);

    @Update("""
            update client_accounts
            set cash_balance = #{cashBalance}
            where account_id = #{accountId}
            """)
    int updateCashBalance(@Param("accountId") long accountId, @Param("cashBalance") BigDecimal cashBalance);
}
