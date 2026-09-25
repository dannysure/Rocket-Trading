package com.rockettrading.rocket_trading.repository;

import com.rockettrading.rocket_trading.repository.model.AuditLogRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;

@Mapper
public interface AuditLogRepository {

    @Insert("""
            insert into audit_logs (entity_name, entity_id, action_type, client_id, state_before, state_after, recorded_at)
            values (#{entityName}, #{entityId}, #{actionType}, #{clientId}, #{stateBefore}, #{stateAfter}, #{recordedAt})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "auditId")
    int insert(AuditLogRecord auditLogRecord);
}
