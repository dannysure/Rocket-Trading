package com.rockettrading.rocket_trading.dto.common;

import java.util.List;

public record ApiErrorResponse(ApiError error) {
    public static ApiErrorResponse of(String code, String message) {
        return new ApiErrorResponse(ApiError.of(code, message, List.of()));
    }

    public static ApiErrorResponse of(String code, String message, List<ErrorDetail> details) {
        return new ApiErrorResponse(ApiError.of(code, message, details));
    }
}
