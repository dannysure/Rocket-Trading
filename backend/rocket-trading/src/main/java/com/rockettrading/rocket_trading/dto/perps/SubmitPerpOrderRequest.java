package com.rockettrading.rocket_trading.dto.perps;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record SubmitPerpOrderRequest(
        @NotBlank(message = "symbol is required")
        @Size(max = 20)
        String symbol,
        @NotBlank(message = "side is required")
        @Pattern(regexp = "BUY|SELL", message = "side must be BUY or SELL")
        String side,
        @NotNull(message = "size is required")
        @DecimalMin(value = "0.000001", message = "size must be positive")
        @Digits(integer = 12, fraction = 6)
        BigDecimal size,
        @NotNull(message = "leverage is required")
        @Min(value = 1, message = "leverage must be at least 1x")
        @Max(value = 100, message = "leverage must be at most 100x")
        Integer leverage,
        Boolean reduceOnly
) {
    public SubmitPerpOrderRequest {
        symbol = symbol == null ? null : symbol.trim().toUpperCase(java.util.Locale.ROOT);
        side = side == null ? null : side.trim().toUpperCase(java.util.Locale.ROOT);
        reduceOnly = reduceOnly != null && reduceOnly;
    }

    public String fingerprint() {
        return String.join("|", symbol, side, size.stripTrailingZeros().toPlainString(),
                String.valueOf(leverage), String.valueOf(reduceOnly));
    }
}
