package com.rockettrading.rocket_trading.futures;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.Objects;

public class PerpetualFuturesCalculator {

    private static final BigDecimal TWO = BigDecimal.valueOf(2);
    private static final int SCALE = 10;

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

    private static BigDecimal requireNonNull(BigDecimal value, String name) {
        return Objects.requireNonNull(value, name);
    }
}
