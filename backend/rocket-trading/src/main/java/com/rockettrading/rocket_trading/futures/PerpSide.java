package com.rockettrading.rocket_trading.futures;

import java.math.BigDecimal;

public enum PerpSide {
    LONG, SHORT;

    /** BUY opens or adds to a long; SELL opens or adds to a short. */
    public static PerpSide fromOrderSide(String orderSide) {
        return switch (orderSide) {
            case "BUY" -> LONG;
            case "SELL" -> SHORT;
            default -> throw new IllegalArgumentException("side must be BUY or SELL");
        };
    }

    public BigDecimal direction() {
        return this == LONG ? BigDecimal.ONE : BigDecimal.ONE.negate();
    }

    public PerpSide opposite() {
        return this == LONG ? SHORT : LONG;
    }
}
