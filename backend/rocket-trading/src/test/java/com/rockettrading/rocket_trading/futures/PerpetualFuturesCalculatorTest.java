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
}
