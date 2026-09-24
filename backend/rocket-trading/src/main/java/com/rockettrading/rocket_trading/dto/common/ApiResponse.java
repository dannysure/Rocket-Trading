package com.rockettrading.rocket_trading.dto.common;

public record ApiResponse<T>(T data, ResponseMeta meta) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(data, ResponseMeta.now());
    }
}
