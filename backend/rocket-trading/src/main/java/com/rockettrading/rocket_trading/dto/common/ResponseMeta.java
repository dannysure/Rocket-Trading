package com.rockettrading.rocket_trading.dto.common;

import java.time.Instant;
import java.util.UUID;

public record ResponseMeta(String requestId, Instant timestamp, String errorMessage) {
    public static ResponseMeta now() {
        return new ResponseMeta(UUID.randomUUID().toString(), Instant.now(), null);
    }

    public static ResponseMeta error(String errorMessage) {
        return new ResponseMeta(UUID.randomUUID().toString(), Instant.now(), errorMessage);
    }
}
