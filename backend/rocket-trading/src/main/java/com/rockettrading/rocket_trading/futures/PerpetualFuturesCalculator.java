package com.rockettrading.rocket_trading.futures;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Pricing and funding for perps whose premium over the spot index comes from this exchange's open interest.
 *
 * <p>The skew (long minus short open interest) sets the premium: {@code premium = clamp(skew / skewScale, ±1%)},
 * and {@code mark = index * (1 + premium)}. Funding charges the crowded side the premium spread over eight hours,
 * so holding the side that pushes the mark away from spot costs exactly that gap, and nothing is charged while
 * longs and shorts balance. That is the incentive that pulls the mark back to the index: the light side is paid
 * to grow until the skew, and with it the premium, closes.
 */
public class PerpetualFuturesCalculator {
    public static final BigDecimal MAX_PREMIUM = new BigDecimal("0.01");
    static final int PRICE_SCALE = 8;
    private static final int RATIO_SCALE = 10;
    private static final BigDecimal TWO = BigDecimal.valueOf(2);
    private static final long SECONDS_PER_RATE_PERIOD = Duration.ofHours(8).toSeconds();

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
     * Funding rate for one interval: the premium, quoted per 8 hours and paid pro rata per interval, so it is
     * proportional to the open-interest imbalance. Positive means longs pay shorts. The premium cap bounds it at
     * 1% per 8 hours.
     */
    public BigDecimal fundingRate(BigDecimal premium, Duration interval) {
        requireNonNull(premium, "premium");
        long seconds = Objects.requireNonNull(interval, "interval").toSeconds();
        if (seconds <= 0) throw new IllegalArgumentException("interval must be positive");
        return premium.multiply(BigDecimal.valueOf(seconds))
                .divide(BigDecimal.valueOf(SECONDS_PER_RATE_PERIOD), RATIO_SCALE, RoundingMode.HALF_UP)
                .stripTrailingZeros();
    }

    /** Signed payment for one interval; positive when a long of this size pays. */
    public BigDecimal fundingPayment(BigDecimal positionSize, BigDecimal markPrice, BigDecimal fundingRate) {
        return requireNonNull(positionSize, "positionSize")
                .multiply(requireNonNull(markPrice, "markPrice"))
                .multiply(requireNonNull(fundingRate, "fundingRate"));
    }

    /** Start of the funding interval containing now; intervals are aligned to the Unix epoch (so hourly is on the hour, UTC). */
    public Instant fundingTime(Instant now, Duration interval) {
        long seconds = Objects.requireNonNull(interval, "interval").toSeconds();
        if (seconds <= 0) throw new IllegalArgumentException("interval must be positive");
        long epoch = Objects.requireNonNull(now, "now").getEpochSecond();
        return Instant.ofEpochSecond(Math.floorDiv(epoch, seconds) * seconds);
    }

    public Instant nextFundingTime(Instant now, Duration interval) {
        return fundingTime(now, interval).plus(interval);
    }

    private static BigDecimal requireNonNull(BigDecimal value, String name) {
        return Objects.requireNonNull(value, name);
    }
}
