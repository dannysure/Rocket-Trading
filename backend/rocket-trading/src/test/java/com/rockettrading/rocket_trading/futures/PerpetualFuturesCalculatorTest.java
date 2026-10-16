package com.rockettrading.rocket_trading.futures;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PerpetualFuturesCalculatorTest {

    private final PerpetualFuturesCalculator calculator = new PerpetualFuturesCalculator();
    private static final Duration HOUR = Duration.ofHours(1);

    @Test
    @DisplayName("index is the quote mid")
    void indexIsTheMid() {
        assertMoney("105", calculator.indexPrice(bd("100"), bd("110")));
    }

    @Test
    @DisplayName("premium is the open-interest skew over the skew scale, capped at 1% either way")
    void premiumFollowsSkew() {
        assertMoney("0", calculator.premium(bd("0"), bd("1000")));
        assertMoney("0.002", calculator.premium(bd("2"), bd("1000")));
        assertMoney("-0.0015", calculator.premium(bd("-1.5"), bd("1000")));
        assertMoney("0.01", calculator.premium(bd("50"), bd("1000")));
        assertMoney("-0.01", calculator.premium(bd("-50"), bd("1000")));
        assertThrows(IllegalArgumentException.class, () -> calculator.premium(bd("1"), bd("0")));
    }

    @Test
    @DisplayName("mark is the index scaled by the premium")
    void markCarriesThePremium() {
        assertMoney("100.2", calculator.withPremium(bd("100"), bd("0.002")));
        assertMoney("99.85", calculator.withPremium(bd("100"), bd("-0.0015")));
    }

    @Test
    @DisplayName("a fill pays the average premium before and after it moves the skew")
    void fillPremiumAveragesBeforeAndAfter() {
        // Opening 2 long from flat: (0 + 0.002) / 2
        assertMoney("0.001", calculator.fillPremium(bd("0"), bd("2"), bd("1000")));
        // Closing it again: (0.002 + 0) / 2, so a round trip has no net price impact
        assertMoney("0.001", calculator.fillPremium(bd("2"), bd("-2"), bd("1000")));
        // A sell into a long-heavy book still trades above the index, but less so than the mark
        assertMoney("0.0045", calculator.fillPremium(bd("5"), bd("-1"), bd("1000")));
        // The cap applies to both ends
        assertMoney("0.01", calculator.fillPremium(bd("20"), bd("5"), bd("1000")));
    }

    @Test
    @DisplayName("nothing is charged while longs and shorts balance")
    void noFundingAtParity() {
        assertMoney("0", calculator.fundingRate(bd("0"), HOUR));
    }

    @Test
    @DisplayName("funding is the premium per 8 hours, paid pro rata per interval, so even a small imbalance is charged")
    void fundingFollowsPremium() {
        assertMoney("0.002", calculator.fundingRate(bd("0.002"), Duration.ofHours(8)));
        assertMoney("0.00025", calculator.fundingRate(bd("0.002"), HOUR));
        assertMoney("0.0000075", calculator.fundingRate(bd("0.0006"), Duration.ofMinutes(6)));
        // shorts outweigh longs: negative, so shorts pay longs
        assertMoney("-0.00125", calculator.fundingRate(bd("-0.01"), HOUR));
        assertMoney("0.0000125", calculator.fundingRate(bd("0.0001"), HOUR));
    }

    @Test
    @DisplayName("a long pays size * mark * rate")
    void fundingPayment() {
        assertMoney("63", calculator.fundingPayment(bd("10"), bd("105"), bd("0.06")));
    }

    @Test
    @DisplayName("funding times are aligned to the interval")
    void fundingTimes() {
        Instant now = Instant.parse("2026-10-14T14:47:55Z");
        assertEquals(Instant.parse("2026-10-14T14:00:00Z"), calculator.fundingTime(now, HOUR));
        assertEquals(Instant.parse("2026-10-14T15:00:00Z"), calculator.nextFundingTime(now, HOUR));
        assertEquals(Instant.parse("2026-10-14T16:00:00Z"), calculator.nextFundingTime(Instant.parse("2026-10-14T15:00:00Z"), HOUR));
        assertEquals(Instant.parse("2026-10-14T14:45:00Z"), calculator.fundingTime(now, Duration.ofMinutes(5)));
        assertEquals(Instant.parse("2026-10-14T14:50:00Z"), calculator.nextFundingTime(now, Duration.ofMinutes(5)));
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), () -> "expected " + expected + " but was " + actual);
    }
}
