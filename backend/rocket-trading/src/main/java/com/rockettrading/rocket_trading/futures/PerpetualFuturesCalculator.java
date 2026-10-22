package com.rockettrading.rocket_trading.futures;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Pricing and funding for perps whose price on this exchange is pushed away from spot by its own open interest.
 *
 * <p>The skew (long minus short open interest) sets the premium: {@code premium = clamp(skew / skewScale, ±1%)},
 * and the perp (mark) price is {@code spot * (1 + premium)}. Every hour, on the hour, funding charges the side
 * that pushed the perp away from spot {@code (mark - spot) / spot / 8} of its position value and pays it to the
 * other side; nothing is charged while the perp trades at spot. Taking the light side is therefore paid, which
 * draws the traders who offset the exchange's own exposure to the skew and pulls the perp back to spot.
 */
public class PerpetualFuturesCalculator {
    public static final BigDecimal MAX_PREMIUM = new BigDecimal("0.01");
    static final int PRICE_SCALE = 8;
    private static final int RATIO_SCALE = 10;
    private static final BigDecimal TWO = BigDecimal.valueOf(2);
    // The premium is charged over eight hourly payments
    private static final BigDecimal FUNDING_PAYMENTS_PER_PREMIUM = BigDecimal.valueOf(8);
    public static final Duration FUNDING_INTERVAL = Duration.ofHours(1);

    /** Spot index from a quote: the mid of its bid and ask. */
    public BigDecimal indexPrice(BigDecimal bid, BigDecimal ask) {
        return requireNonNull(bid, "bid").add(requireNonNull(ask, "ask")).divide(TWO, RATIO_SCALE, RoundingMode.HALF_UP);
    }

    /** Premium of the perp over its index for a given skew (base units). */
    public BigDecimal premium(BigDecimal skew, BigDecimal skewScale) {
        requireNonNull(skew, "skew");
        if (requireNonNull(skewScale, "skewScale").signum() <= 0) throw new IllegalArgumentException("skewScale must be positive");
        return skew.divide(skewScale, RATIO_SCALE, RoundingMode.HALF_UP).max(MAX_PREMIUM.negate()).min(MAX_PREMIUM);
    }

    /** Applies a premium to a spot price, e.g. the index or the index bid/ask. */
    public BigDecimal withPremium(BigDecimal spotPrice, BigDecimal premium) {
        return requireNonNull(spotPrice, "spotPrice").multiply(BigDecimal.ONE.add(requireNonNull(premium, "premium")))
                .setScale(PRICE_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Premium a fill pays: the average of the premium before and after it moves the skew by signedSize
     * (positive for buys). Opening and then closing the same size therefore nets out to zero price impact,
     * so a trader cannot profit from the mark move their own order causes.
     */
    public BigDecimal fillPremium(BigDecimal skew, BigDecimal signedSize, BigDecimal skewScale) {
        BigDecimal before = premium(skew, skewScale);
        BigDecimal after = premium(skew.add(requireNonNull(signedSize, "signedSize")), skewScale);
        return before.add(after).divide(TWO, RATIO_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Hourly funding rate from how far the perp trades from spot: {@code (mark - spot) / spot / 8}. Positive means
     * the perp is above spot and longs pay shorts; negative means shorts pay longs. The premium cap bounds it at
     * 0.125% an hour.
     */
    public BigDecimal fundingRate(BigDecimal markPrice, BigDecimal spotPrice) {
        BigDecimal spot = requireNonNull(spotPrice, "spotPrice");
        if (spot.signum() <= 0) throw new IllegalArgumentException("spotPrice must be positive");
        return requireNonNull(markPrice, "markPrice").subtract(spot)
                .divide(spot.multiply(FUNDING_PAYMENTS_PER_PREMIUM), RATIO_SCALE, RoundingMode.HALF_UP)
                .stripTrailingZeros();
    }

    /** Signed payment for one hour; positive when a long of this size pays. */
    public BigDecimal fundingPayment(BigDecimal positionSize, BigDecimal markPrice, BigDecimal fundingRate) {
        return requireNonNull(positionSize, "positionSize")
                .multiply(requireNonNull(markPrice, "markPrice"))
                .multiply(requireNonNull(fundingRate, "fundingRate"));
    }

    /** The hour (UTC) containing now: positions held over it pay or receive funding once for it. */
    public Instant fundingTime(Instant now) {
        return Objects.requireNonNull(now, "now").truncatedTo(ChronoUnit.HOURS);
    }

    public Instant nextFundingTime(Instant now) {
        return fundingTime(now).plus(FUNDING_INTERVAL);
    }

    private static BigDecimal requireNonNull(BigDecimal value, String name) {
        return Objects.requireNonNull(value, name);
    }
}
