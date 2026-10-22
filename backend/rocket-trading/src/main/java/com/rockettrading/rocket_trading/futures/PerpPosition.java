package com.rockettrading.rocket_trading.futures;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * An isolated-margin perpetual position. Margin is the collateral posted to this position alone,
 * so losses beyond it are cut off by liquidation instead of drawing on the rest of the account.
 */
public record PerpPosition(PerpSide side, BigDecimal size, BigDecimal entryPrice, BigDecimal margin, int leverage) {
    static final int MONEY_SCALE = 4;
    static final int RATIO_SCALE = 6;

    private static final PerpetualFuturesCalculator CALCULATOR = new PerpetualFuturesCalculator();

    public PerpPosition {
        Objects.requireNonNull(side, "side");
        Objects.requireNonNull(size, "size");
        Objects.requireNonNull(entryPrice, "entryPrice");
        Objects.requireNonNull(margin, "margin");
        if (size.signum() <= 0) throw new IllegalArgumentException("size must be positive");
        if (entryPrice.signum() <= 0) throw new IllegalArgumentException("entryPrice must be positive");
        if (leverage < 1) throw new IllegalArgumentException("leverage must be at least 1");
    }

    public BigDecimal notional(BigDecimal markPrice) {
        return markPrice.multiply(size).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    public BigDecimal unrealizedPnl(BigDecimal markPrice) {
        return markPrice.subtract(entryPrice).multiply(size).multiply(side.direction())
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    public BigDecimal equity(BigDecimal markPrice) {
        return margin.add(unrealizedPnl(markPrice));
    }

    public BigDecimal returnOnEquity(BigDecimal markPrice) {
        return unrealizedPnl(markPrice).divide(margin, RATIO_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Mark price at which equity equals maintenance margin.
     * Long: margin + (P - E)s = P s mmr. Short: margin + (E - P)s = P s mmr.
     * Returns null when no positive price can trigger liquidation.
     */
    public BigDecimal liquidationPrice(BigDecimal maintenanceMarginRate) {
        BigDecimal entryNotional = entryPrice.multiply(size);
        BigDecimal numerator = side == PerpSide.LONG ? entryNotional.subtract(margin) : entryNotional.add(margin);
        BigDecimal denominator = size.multiply(side == PerpSide.LONG
                ? BigDecimal.ONE.subtract(maintenanceMarginRate)
                : BigDecimal.ONE.add(maintenanceMarginRate));
        if (numerator.signum() <= 0) return null;
        return numerator.divide(denominator, MONEY_SCALE, RoundingMode.HALF_UP);
    }

    public boolean isLiquidatable(BigDecimal markPrice, BigDecimal maintenanceMarginRate) {
        BigDecimal maintenanceMargin = markPrice.multiply(size).multiply(maintenanceMarginRate);
        return equity(markPrice).compareTo(maintenanceMargin) <= 0;
    }

    /** Positive rates are paid by longs to shorts; payments settle against this position's isolated margin. */
    public PerpFunding applyFunding(BigDecimal markPrice, BigDecimal fundingRate) {
        BigDecimal payment = CALCULATOR.fundingPayment(size, markPrice, fundingRate)
                .multiply(side.direction())
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        return new PerpFunding(new PerpPosition(side, size, entryPrice, margin.subtract(payment), leverage), payment);
    }
}
