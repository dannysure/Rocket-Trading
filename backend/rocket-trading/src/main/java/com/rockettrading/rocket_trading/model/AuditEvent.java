package com.rockettrading.rocket_trading.model;

import java.time.Instant;

public class AuditEvent {
    private String eventType;
    private Instant occurredAt;
    private String recordActor;
}

