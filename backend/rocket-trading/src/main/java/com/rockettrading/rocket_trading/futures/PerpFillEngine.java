package com.rockettrading.rocket_trading.futures;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static com.rockettrading.rocket_trading.futures.PerpPosition.MONEY_SCALE;

/** Applies a market fill to a single net isolated-margin position. Pure: callers persist the outcome. */
public class PerpFillEngine {
    private static final int PRICE_SCALE = 8;

    public PerpFillOutcome fill(PerpMarketSpec market, PerpPosition current, PerpSide side,
                                BigDecimal size, BigDecimal price, int leverage, boolean reduceOnly) {
        if (size == null || size.signum() <= 0) throw new IllegalArgumentException("size must be positive");
        if (price == null || price.signum() <= 0) throw new IllegalArgumentException("price must be positive");
        if (leverage < 1 || leverage > market.maxLeverage()) {
            throw new IllegalArgumentException("leverage must be between 1x and " + market.maxLeverage() + "x");
        }

        if (current == null || current.side() == side) {
            if (reduceOnly) throw new IllegalArgumentException("Reduce-only order would open or increase a position");
            if (current != null && current.leverage() != leverage) {
                throw new IllegalArgumentException("Leverage must match the open position (" + current.leverage() + "x)");
            }
            return increase(market, current, side, size, price, leverage);
        }
        return reduce(market, current, side, reduceOnly ? size.min(current.size()) : size, price, leverage);
    }

    private PerpFillOutcome increase(PerpMarketSpec market, PerpPosition current, PerpSide side,
                                     BigDecimal size, BigDecimal price, int leverage) {
        BigDecimal margin = initialMargin(size, price, leverage);
        BigDecimal fee = fee(market, size, price);
        PerpPosition position;
        if (current == null) {
            position = new PerpPosition(side, size, price, margin, leverage);
        } else {
            BigDecimal newSize = current.size().add(size);
            BigDecimal entry = current.entryPrice().multiply(current.size()).add(price.multiply(size))
                    .divide(newSize, PRICE_SCALE, RoundingMode.HALF_UP);
            position = new PerpPosition(side, newSize, entry, current.margin().add(margin), leverage);
        }
        return new PerpFillOutcome(position, size, BigDecimal.ZERO, fee, margin.add(fee).negate());
    }

    private PerpFillOutcome reduce(PerpMarketSpec market, PerpPosition current, PerpSide side,
                                   BigDecimal size, BigDecimal price, int leverage) {
        BigDecimal closeSize = size.min(current.size());
        BigDecimal realized = price.subtract(current.entryPrice()).multiply(closeSize)
                .multiply(current.side().direction()).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal released = current.margin().multiply(closeSize)
                .divide(current.size(), MONEY_SCALE, RoundingMode.HALF_UP);

        // Isolated margin: what comes back is capped below at zero, and the close fee is taken from it first
        BigDecimal returned = released.add(realized).max(BigDecimal.ZERO);
        BigDecimal closeFee = fee(market, closeSize, price).min(returned);
        BigDecimal cashDelta = returned.subtract(closeFee);
        BigDecimal fee = closeFee;

        PerpPosition position = closeSize.compareTo(current.size()) == 0 ? null
                : new PerpPosition(current.side(), current.size().subtract(closeSize), current.entryPrice(),
                        current.margin().subtract(released), current.leverage());

        BigDecimal remainder = size.subtract(closeSize);
        if (remainder.signum() > 0) {
            PerpFillOutcome opened = increase(market, null, side, remainder, price, leverage);
            position = opened.position();
            fee = fee.add(opened.fee());
            cashDelta = cashDelta.add(opened.cashDelta());
        }
        return new PerpFillOutcome(position, size, realized, fee, cashDelta);
    }

    private BigDecimal initialMargin(BigDecimal size, BigDecimal price, int leverage) {
        return price.multiply(size).divide(BigDecimal.valueOf(leverage), MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal fee(PerpMarketSpec market, BigDecimal size, BigDecimal price) {
        return price.multiply(size).multiply(market.takerFeeRate()).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
