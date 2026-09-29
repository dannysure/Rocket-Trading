package com.rockettrading.rocket_trading.dto.order;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record SubmitOrderRequest(
        @NotBlank(message = "symbol is required")
        @Size(max = 40)
        String symbol,
        @NotBlank(message = "side is required")
        @Pattern(regexp = "BUY|SELL", message = "side must be BUY or SELL")
        String side,
        @DecimalMin(value = "0.000001", message = "quantity must be positive")
        @NotNull(message = "quantity is required")
        @Digits(integer = 12, fraction = 6)
        BigDecimal quantity,
        @Pattern(regexp = "stock|crypto", message = "market must be stock or crypto")
        String market,
        @Pattern(regexp = "MARKET|LIMIT", message = "orderType must be MARKET or LIMIT")
        String orderType,
        @DecimalMin(value = "0.0001", message = "limitPrice must be positive")
        @Digits(integer = 12, fraction = 4)
        BigDecimal limitPrice
) {
    public SubmitOrderRequest {
        symbol = symbol == null ? null : symbol.trim().toUpperCase(java.util.Locale.ROOT);
        side = side == null ? null : side.trim().toUpperCase(java.util.Locale.ROOT);
        market = market == null || market.isBlank() ? "stock" : market.trim().toLowerCase(java.util.Locale.ROOT);
        orderType = orderType == null || orderType.isBlank() ? "MARKET" : orderType.trim().toUpperCase(java.util.Locale.ROOT);
    }

    @AssertTrue(message = "LIMIT orders require limitPrice; MARKET orders must omit it")
    public boolean isLimitPriceValid() {
        return "LIMIT".equals(orderType) ? limitPrice != null : limitPrice == null;
    }

    public String fingerprint() {
        return String.join("|", symbol, side, quantity.stripTrailingZeros().toPlainString(), market,
                orderType, limitPrice == null ? "" : limitPrice.stripTrailingZeros().toPlainString());
    }
}
