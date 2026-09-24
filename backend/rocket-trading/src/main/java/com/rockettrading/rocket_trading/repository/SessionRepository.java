package com.rockettrading.rocket_trading.repository;

import com.rockettrading.rocket_trading.model.Session;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;

@Mapper
public interface SessionRepository {

    @Select("""
            select session_id, expires_at
            from client_sessions
            where session_id = #{sessionId}
            """)
    Session findBySessionId(long sessionId);

    @Insert("""
            insert into client_sessions (session_id, client_id, expires_at)
            values (#{sessionId}, #{clientId}, #{expiresAt})
            """)
    int insert(@Param("sessionId") long sessionId,
               @Param("clientId") long clientId,
               @Param("expiresAt") Instant expiresAt);

    @Update("""
            update client_sessions
            set expires_at = #{expiresAt}
            where session_id = #{sessionId}
            """)
    int updateExpiresAt(@Param("sessionId") long sessionId, @Param("expiresAt") Instant expiresAt);
}
