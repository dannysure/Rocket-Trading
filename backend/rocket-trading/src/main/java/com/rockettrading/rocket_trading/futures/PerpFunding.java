package com.rockettrading.rocket_trading.futures;

import java.math.BigDecimal;

/** Result of one funding interval: payment is positive when the position paid, negative when it received. */
public record PerpFunding(PerpPosition position, BigDecimal payment) {
}
