package com.rockettrading.rocket_trading.dto.common;

import java.time.Instant;
import java.util.UUID;

public record ResponseMeta(String requestId, Instant timestamp) {
    public static ResponseMeta now() {
        return new ResponseMeta(UUID.randomUUID().toString(), Instant.now());
    }
}
