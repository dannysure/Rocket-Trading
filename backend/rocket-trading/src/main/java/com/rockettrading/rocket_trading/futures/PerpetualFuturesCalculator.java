package com.rockettrading.rocket_trading.futures;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

public class PerpetualFuturesCalculator {

    private static final BigDecimal TWO = BigDecimal.valueOf(2);
    private static final int SCALE = 10;
    private static final BigDecimal EIGHT_HOUR_INTEREST_RATE = new BigDecimal("0.0001");
    private static final BigDecimal INTEREST_CLAMP = new BigDecimal("0.0005");
    private static final BigDecimal FUNDING_INTERVALS_PER_RATE = BigDecimal.valueOf(8);
    private static final BigDecimal MAX_HOURLY_FUNDING = new BigDecimal("0.04");

    public BigDecimal calculatePerpPrice(BigDecimal bestBid, BigDecimal bestAsk) {
        return requireNonNull(bestBid, "bestBid")
                .add(requireNonNull(bestAsk, "bestAsk"))
                .divide(TWO, SCALE, RoundingMode.HALF_UP)
                .stripTrailingZeros();
    }

    public BigDecimal calculatePremium(BigDecimal perpPrice, BigDecimal indexPrice) {
        BigDecimal safePerpPrice = requireNonNull(perpPrice, "perpPrice");
        BigDecimal safeIndexPrice = requireNonNull(indexPrice, "indexPrice");

        return safePerpPrice
                .subtract(safeIndexPrice)
                .divide(safeIndexPrice, SCALE, RoundingMode.HALF_UP)
                .stripTrailingZeros();
    }

    public BigDecimal calculateFundingRate(BigDecimal premium, BigDecimal interestRate) {
        return requireNonNull(premium, "premium")
                .add(requireNonNull(interestRate, "interestRate"))
                .stripTrailingZeros();
    }

    public BigDecimal calculateFundingPayment(BigDecimal positionSize, BigDecimal markPrice, BigDecimal fundingRate) {
        return requireNonNull(positionSize, "positionSize")
                .multiply(requireNonNull(markPrice, "markPrice"))
                .multiply(requireNonNull(fundingRate, "fundingRate"))
                .stripTrailingZeros();
    }

    public Duration fundingCadence() {
        return Duration.ofHours(1);
    }

    /**
     * Hourly funding rate: premium plus interest clamped to within 0.05% of the premium, quoted per 8 hours
     * and paid in 8 hourly instalments, capped at 4% per hour.
     */
    public BigDecimal hourlyFundingRate(BigDecimal markPrice, BigDecimal oraclePrice) {
        BigDecimal premium = calculatePremium(markPrice, oraclePrice);
        BigDecimal interestAdjustment = EIGHT_HOUR_INTEREST_RATE.subtract(premium)
                .max(INTEREST_CLAMP.negate()).min(INTEREST_CLAMP);
        BigDecimal hourly = calculateFundingRate(premium, interestAdjustment)
                .divide(FUNDING_INTERVALS_PER_RATE, SCALE, RoundingMode.HALF_UP);
        return hourly.max(MAX_HOURLY_FUNDING.negate()).min(MAX_HOURLY_FUNDING).stripTrailingZeros();
    }

    public Instant nextFundingTime(Instant now) {
        return Objects.requireNonNull(now, "now").truncatedTo(ChronoUnit.HOURS).plus(fundingCadence());
    }

    private static BigDecimal requireNonNull(BigDecimal value, String name) {
        return Objects.requireNonNull(value, name);
    }
}
