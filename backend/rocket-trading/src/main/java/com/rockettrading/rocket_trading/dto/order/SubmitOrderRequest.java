package com.rockettrading.rocket_trading.dto.order;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

public record SubmitOrderRequest(
        @NotBlank(message = "symbol is required")
        String symbol,
        @NotBlank(message = "side is required")
        String side,
        @DecimalMin(value = "0.000001", message = "quantity must be positive")
        BigDecimal quantity,
        String market,
        String orderType,
        BigDecimal limitPrice
) {
}
