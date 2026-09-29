package com.rockettrading.rocket_trading.repository;

import com.rockettrading.rocket_trading.model.Client;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;

@Mapper
public interface ClientRepository {

    @Select("""
            select client_id, client_full_name as name, email_address as email
            from client_profiles
            where client_id = #{clientId}
            """)
    Client findById(long clientId);

    @Select("""
            select client_id, client_full_name as name, email_address as email
            from client_profiles
            where lower(email_address) = lower(#{email})
            """)
    Client findByEmail(String email);

    @Insert("""
            insert into client_profiles (client_id, client_full_name, email_address, date_of_birth, risk_profile)
            values (#{client.clientId}, #{client.name}, #{client.email}, #{dateOfBirth}, #{riskProfile})
            """)
    int insertProfile(@Param("client") Client client,
                      @Param("dateOfBirth") LocalDate dateOfBirth,
                      @Param("riskProfile") String riskProfile);
}
