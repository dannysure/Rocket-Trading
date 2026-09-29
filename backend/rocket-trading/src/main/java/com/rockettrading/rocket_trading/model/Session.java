package com.rockettrading.rocket_trading.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import java.time.Instant;

@Entity
public class Session {
    @Id
    private long sessionId;
    private Instant expiresAt;

    public Session() {
    }

    public Session(long sessionId, Instant expiresAt) {
        setSessionId(sessionId);
        setExpiresAt(expiresAt);
    }

    public long getSessionId() {
        return sessionId;
    }

    public void setSessionId(long sessionId) {
        if (sessionId <= 0) {
            throw new IllegalArgumentException("sessionId must be positive");
        }
        this.sessionId = sessionId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        if (expiresAt == null) {
            throw new IllegalArgumentException("expiresAt must not be null");
        }
        this.expiresAt = expiresAt;
    }

    public void revoke() {
        revoke(Instant.EPOCH);
    }

    public void revoke(Instant revokedAt) {
        if (revokedAt == null) {
            throw new IllegalArgumentException("revokedAt must not be null");
        }
        this.expiresAt = revokedAt;
    }

    public boolean isActive() {
        return isActive(Instant.now());
    }

    public boolean isActive(Instant asOf) {
        if (asOf == null) {
            throw new IllegalArgumentException("asOf must not be null");
        }
        return expiresAt != null && expiresAt.isAfter(asOf);
    }
}
