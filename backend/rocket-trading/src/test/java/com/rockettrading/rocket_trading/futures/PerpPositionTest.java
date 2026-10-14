package com.rockettrading.rocket_trading.futures;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class PerpPositionTest {
    // 20x max leverage -> maintenance margin rate is half the 5% initial margin
    private static final BigDecimal MMR = new BigDecimal("0.025");

    @Test
    @DisplayName("long gains when mark rises above entry")
    void longUnrealizedPnl() {
        PerpPosition long2 = new PerpPosition(PerpSide.LONG, bd("2"), bd("100"), bd("20"), 10);
        assertMoney("10", long2.unrealizedPnl(bd("105")));
        assertMoney("-10", long2.unrealizedPnl(bd("95")));
    }

    @Test
    @DisplayName("short gains when mark falls below entry")
    void shortUnrealizedPnl() {
        PerpPosition short2 = new PerpPosition(PerpSide.SHORT, bd("2"), bd("100"), bd("20"), 10);
        assertMoney("10", short2.unrealizedPnl(bd("95")));
        assertMoney("-10", short2.unrealizedPnl(bd("105")));
    }

    @Test
    @DisplayName("return on equity is unrealized pnl over posted margin")
    void returnOnEquity() {
        PerpPosition position = new PerpPosition(PerpSide.LONG, bd("2"), bd("100"), bd("20"), 10);
        assertMoney("0.5", position.returnOnEquity(bd("105")));
    }

    @Test
    @DisplayName("long liquidates where equity falls to maintenance margin")
    void longLiquidationPrice() {
        // margin 10, size 1, entry 100: 10 + (P - 100) = 0.025P  ->  P = 90 / 0.975
        PerpPosition position = new PerpPosition(PerpSide.LONG, bd("1"), bd("100"), bd("10"), 10);
        assertMoney("92.3077", position.liquidationPrice(MMR));
    }

    @Test
    @DisplayName("short liquidates where equity falls to maintenance margin")
    void shortLiquidationPrice() {
        // margin 10, size 1, entry 100: 10 + (100 - P) = 0.025P  ->  P = 110 / 1.025
        PerpPosition position = new PerpPosition(PerpSide.SHORT, bd("1"), bd("100"), bd("10"), 10);
        assertMoney("107.3171", position.liquidationPrice(MMR));
    }

    @Test
    @DisplayName("a fully collateralised long has no liquidation price")
    void oneTimesLongCannotBeLiquidated() {
        PerpPosition position = new PerpPosition(PerpSide.LONG, bd("1"), bd("100"), bd("100"), 1);
        assertNull(position.liquidationPrice(MMR));
    }

    @Test
    @DisplayName("liquidatable once mark crosses the liquidation price")
    void liquidatableAtOrBeyondLiquidationPrice() {
        PerpPosition position = new PerpPosition(PerpSide.LONG, bd("1"), bd("100"), bd("10"), 10);
        assertFalse(position.isLiquidatable(bd("92.5"), MMR));
        assertTrue(position.isLiquidatable(bd("92.3"), MMR));
    }

    @Test
    @DisplayName("positive funding is paid by longs out of their isolated margin")
    void longPaysPositiveFunding() {
        PerpPosition position = new PerpPosition(PerpSide.LONG, bd("2"), bd("100"), bd("20"), 10);
        PerpFunding funding = position.applyFunding(bd("100"), bd("0.001"));
        assertMoney("0.2", funding.payment());
        assertMoney("19.8", funding.position().margin());
    }

    @Test
    @DisplayName("positive funding is received by shorts into their isolated margin")
    void shortReceivesPositiveFunding() {
        PerpPosition position = new PerpPosition(PerpSide.SHORT, bd("2"), bd("100"), bd("20"), 10);
        PerpFunding funding = position.applyFunding(bd("100"), bd("0.001"));
        assertMoney("-0.2", funding.payment());
        assertMoney("20.2", funding.position().margin());
    }

    @Test
    @DisplayName("rejects malformed positions")
    void rejectsInvalidPositions() {
        assertThrows(IllegalArgumentException.class, () -> new PerpPosition(PerpSide.LONG, bd("0"), bd("100"), bd("10"), 10));
        assertThrows(IllegalArgumentException.class, () -> new PerpPosition(PerpSide.LONG, bd("1"), bd("0"), bd("10"), 10));
        assertThrows(IllegalArgumentException.class, () -> new PerpPosition(PerpSide.LONG, bd("1"), bd("100"), bd("10"), 0));
        assertThrows(NullPointerException.class, () -> new PerpPosition(null, bd("1"), bd("100"), bd("10"), 10));
    }

    static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    static void assertMoney(String expected, BigDecimal actual) {
        assertNotNull(actual);
        assertEquals(0, new BigDecimal(expected).compareTo(actual), () -> "expected " + expected + " but was " + actual);
    }
}
