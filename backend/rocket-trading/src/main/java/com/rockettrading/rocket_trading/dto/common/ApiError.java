package com.rockettrading.rocket_trading.dto.common;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ApiError(
        String code,
        String message,
        List<ErrorDetail> details,
        String requestId,
        Instant timestamp
) {
    public static ApiError of(String code, String message, List<ErrorDetail> details) {
        return new ApiError(code, message, details, UUID.randomUUID().toString(), Instant.now());
    }
}
