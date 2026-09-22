package com.rockettrading.rocket_trading.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import java.time.Instant;

@Entity
public class Session {
    @Id
    private long sessionId;
    private Instant expiresAt;
}
