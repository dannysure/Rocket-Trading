package com.rockettrading.rocket_trading.repository.model;

import lombok.Data;

import java.time.Instant;

@Data
public class AuditLogRecord {
    private Long auditId;
    private String entityName;
    private Long entityId;
    private String actionType;
    private Long clientId;
    private String stateBefore;
    private String stateAfter;
    private Instant recordedAt;
}
