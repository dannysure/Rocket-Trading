package com.rockettrading.rocket_trading.futures;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PerpetualFuturesCalculatorTest {

    private final PerpetualFuturesCalculator calculator = new PerpetualFuturesCalculator();

    @Test
    @DisplayName("calculates perp price, premium, funding rate, and funding payment")
    void calculatesPerpPricePremiumFundingRateAndFundingPayment() {
        BigDecimal perpPrice = calculator.calculatePerpPrice(new BigDecimal("100"), new BigDecimal("110"));
        assertEquals(0, perpPrice.compareTo(new BigDecimal("105")));

        BigDecimal premium = calculator.calculatePremium(perpPrice, new BigDecimal("100"));
        assertEquals(0, premium.compareTo(new BigDecimal("0.05")));

        BigDecimal fundingRate = calculator.calculateFundingRate(premium, new BigDecimal("0.01"));
        assertEquals(0, fundingRate.compareTo(new BigDecimal("0.06")));

        BigDecimal fundingPayment = calculator.calculateFundingPayment(new BigDecimal("10"), perpPrice, fundingRate);
        assertEquals(0, fundingPayment.compareTo(new BigDecimal("63")));
    }

    @Test
    @DisplayName("uses hourly funding cadence")
    void usesHourlyFundingCadence() {
        assertEquals(Duration.ofHours(1), calculator.fundingCadence());
    }

    @Test
    @DisplayName("hourly funding is the base interest rate when perp trades at the oracle")
    void hourlyFundingAtParity() {
        // 0.01% per 8h interest, paid hourly
        assertEquals(0, calculator.hourlyFundingRate(new BigDecimal("100"), new BigDecimal("100"))
                .compareTo(new BigDecimal("0.0000125")));
    }

    @Test
    @DisplayName("hourly funding follows the premium with interest clamped to 0.05%")
    void hourlyFundingFollowsPremium() {
        // premium 1%, interest - premium clamped to -0.05%, then spread across 8 hourly payments
        assertEquals(0, calculator.hourlyFundingRate(new BigDecimal("101"), new BigDecimal("100"))
                .compareTo(new BigDecimal("0.0011875")));
    }

    @Test
    @DisplayName("hourly funding is capped at 4% either way")
    void hourlyFundingIsCapped() {
        assertEquals(0, calculator.hourlyFundingRate(new BigDecimal("200"), new BigDecimal("100"))
                .compareTo(new BigDecimal("0.04")));
        assertEquals(0, calculator.hourlyFundingRate(new BigDecimal("10"), new BigDecimal("100"))
                .compareTo(new BigDecimal("-0.04")));
    }

    @Test
    @DisplayName("funding settles at the top of each hour")
    void nextFundingTime() {
        assertEquals(java.time.Instant.parse("2026-10-14T15:00:00Z"),
                calculator.nextFundingTime(java.time.Instant.parse("2026-10-14T14:47:55Z")));
        assertEquals(java.time.Instant.parse("2026-10-14T16:00:00Z"),
                calculator.nextFundingTime(java.time.Instant.parse("2026-10-14T15:00:00Z")));
    }
}
