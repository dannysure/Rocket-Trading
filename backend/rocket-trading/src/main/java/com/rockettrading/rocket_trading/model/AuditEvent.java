package com.rockettrading.rocket_trading.model;

import java.time.Instant;

public class AuditEvent {
    private String eventType;
    private Instant occurredAt;
    private String recordActor;

    public AuditEvent() {
    }

    public AuditEvent(String eventType, Instant occurredAt, String recordActor) {
        this.eventType = eventType;
        this.occurredAt = occurredAt;
        this.recordActor = recordActor;
    }

    public String getEventType() {
        return eventType;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getRecordActor() {
        return recordActor;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }

    public void setRecordActor(String recordActor) {
        this.recordActor = recordActor;
    }
}

