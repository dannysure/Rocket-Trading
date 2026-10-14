package com.rockettrading.rocket_trading.repository;

import com.rockettrading.rocket_trading.repository.model.AuditLogRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AuditLogRepository {

    @Insert("""
            insert into audit_logs (entity_name, entity_id, action_type, client_id, state_before, state_after, recorded_at)
            values (#{entityName}, #{entityId}, #{actionType}, #{clientId}, #{stateBefore}, #{stateAfter}, #{recordedAt})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "auditId")
    int insert(AuditLogRecord auditLogRecord);

    @Select("""
            select audit_id, entity_name, entity_id, action_type, client_id, state_before, state_after, recorded_at
            from audit_logs
            where client_id = #{clientId}
              and (
                    (entity_name = 'orders' and entity_id = #{orderId})
                    or (entity_name = 'fills' and entity_id in (
                        select fill_id from fills where order_id = #{orderId}
                    ))
                    or (entity_name in ('client_accounts', 'account_holdings')
                        and state_after like ('%"orderId":' || #{orderId} || '%'))
              )
            order by recorded_at asc, audit_id asc
            """)
    List<AuditLogRecord> findTimelineForOrder(long clientId, long orderId);
}
