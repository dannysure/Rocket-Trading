package com.rockettrading.rocket_trading.futures;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.rockettrading.rocket_trading.futures.PerpPositionTest.assertMoney;
import static com.rockettrading.rocket_trading.futures.PerpPositionTest.bd;
import static org.junit.jupiter.api.Assertions.*;

class PerpFillEngineTest {
    // 0.045% taker fee, 20x max leverage
    private final PerpMarketSpec market = new PerpMarketSpec("BTC-PERP", 20, bd("0.00045"));
    private final PerpFillEngine engine = new PerpFillEngine();

    @Test
    @DisplayName("opening from flat posts notional / leverage as margin and pays the taker fee")
    void opensNewPosition() {
        PerpFillOutcome outcome = engine.fill(market, null, PerpSide.LONG, bd("2"), bd("100"), 10, false);

        assertEquals(PerpSide.LONG, outcome.position().side());
        assertMoney("2", outcome.position().size());
        assertMoney("100", outcome.position().entryPrice());
        assertMoney("20", outcome.position().margin());
        assertEquals(10, outcome.position().leverage());
        assertMoney("2", outcome.filledSize());
        assertMoney("0", outcome.realizedPnl());
        assertMoney("0.09", outcome.fee());
        assertMoney("-20.09", outcome.cashDelta());
    }

    @Test
    @DisplayName("adding to a position averages the entry price and adds margin")
    void increasesPosition() {
        PerpPosition existing = new PerpPosition(PerpSide.LONG, bd("2"), bd("100"), bd("20"), 10);

        PerpFillOutcome outcome = engine.fill(market, existing, PerpSide.LONG, bd("2"), bd("110"), 10, false);

        assertMoney("4", outcome.position().size());
        assertMoney("105", outcome.position().entryPrice());
        assertMoney("42", outcome.position().margin());
        assertMoney("0.099", outcome.fee());
        assertMoney("-22.099", outcome.cashDelta());
    }

    @Test
    @DisplayName("adding with a different leverage than the open position is rejected")
    void rejectsLeverageChangeOnIncrease() {
        PerpPosition existing = new PerpPosition(PerpSide.LONG, bd("2"), bd("100"), bd("20"), 10);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> engine.fill(market, existing, PerpSide.LONG, bd("1"), bd("100"), 5, false));
        assertTrue(error.getMessage().contains("10x"));
    }

    @Test
    @DisplayName("partially closing realizes pnl and releases margin pro rata")
    void partiallyClosesPosition() {
        PerpPosition existing = new PerpPosition(PerpSide.LONG, bd("2"), bd("100"), bd("20"), 10);

        PerpFillOutcome outcome = engine.fill(market, existing, PerpSide.SHORT, bd("1"), bd("110"), 10, false);

        assertMoney("1", outcome.position().size());
        assertMoney("100", outcome.position().entryPrice());
        assertMoney("10", outcome.position().margin());
        assertMoney("10", outcome.realizedPnl());
        assertMoney("0.0495", outcome.fee());
        assertMoney("19.9505", outcome.cashDelta());
    }

    @Test
    @DisplayName("fully closing a short leaves the account flat")
    void fullyClosesShort() {
        PerpPosition existing = new PerpPosition(PerpSide.SHORT, bd("2"), bd("100"), bd("20"), 10);

        PerpFillOutcome outcome = engine.fill(market, existing, PerpSide.LONG, bd("2"), bd("90"), 10, false);

        assertNull(outcome.position());
        assertMoney("20", outcome.realizedPnl());
        assertMoney("0.081", outcome.fee());
        assertMoney("39.919", outcome.cashDelta());
    }

    @Test
    @DisplayName("a loss beyond the isolated margin never debits the rest of the account")
    void lossIsCappedAtIsolatedMargin() {
        PerpPosition existing = new PerpPosition(PerpSide.LONG, bd("1"), bd("100"), bd("10"), 10);

        PerpFillOutcome outcome = engine.fill(market, existing, PerpSide.SHORT, bd("1"), bd("85"), 10, false);

        assertNull(outcome.position());
        assertMoney("-15", outcome.realizedPnl());
        assertMoney("0", outcome.cashDelta());
    }

    @Test
    @DisplayName("an opposite order larger than the position closes it and opens the remainder")
    void flipsPosition() {
        PerpPosition existing = new PerpPosition(PerpSide.LONG, bd("1"), bd("100"), bd("10"), 10);

        PerpFillOutcome outcome = engine.fill(market, existing, PerpSide.SHORT, bd("3"), bd("110"), 10, false);

        assertEquals(PerpSide.SHORT, outcome.position().side());
        assertMoney("2", outcome.position().size());
        assertMoney("110", outcome.position().entryPrice());
        assertMoney("22", outcome.position().margin());
        assertMoney("10", outcome.realizedPnl());
        assertMoney("0.1485", outcome.fee());
        assertMoney("-2.1485", outcome.cashDelta());
    }

    @Test
    @DisplayName("reduce-only orders are clamped to the open size")
    void reduceOnlyClampsToPositionSize() {
        PerpPosition existing = new PerpPosition(PerpSide.LONG, bd("1"), bd("100"), bd("10"), 10);

        PerpFillOutcome outcome = engine.fill(market, existing, PerpSide.SHORT, bd("5"), bd("100"), 10, true);

        assertNull(outcome.position());
        assertMoney("1", outcome.filledSize());
    }

    @Test
    @DisplayName("reduce-only orders cannot open or increase a position")
    void reduceOnlyCannotIncrease() {
        PerpPosition existing = new PerpPosition(PerpSide.LONG, bd("1"), bd("100"), bd("10"), 10);

        assertThrows(IllegalArgumentException.class,
                () -> engine.fill(market, null, PerpSide.LONG, bd("1"), bd("100"), 10, true));
        assertThrows(IllegalArgumentException.class,
                () -> engine.fill(market, existing, PerpSide.LONG, bd("1"), bd("100"), 10, true));
    }

    @Test
    @DisplayName("leverage outside 1..max and non-positive sizes are rejected")
    void rejectsInvalidOrders() {
        assertThrows(IllegalArgumentException.class,
                () -> engine.fill(market, null, PerpSide.LONG, bd("1"), bd("100"), 21, false));
        assertThrows(IllegalArgumentException.class,
                () -> engine.fill(market, null, PerpSide.LONG, bd("1"), bd("100"), 0, false));
        assertThrows(IllegalArgumentException.class,
                () -> engine.fill(market, null, PerpSide.LONG, bd("0"), bd("100"), 10, false));
    }

    @Test
    @DisplayName("maintenance margin rate is half the initial margin at max leverage")
    void maintenanceMarginRate() {
        assertMoney("0.025", market.maintenanceMarginRate());
        assertMoney("0.1", new PerpMarketSpec("X", 5, bd("0.00045")).maintenanceMarginRate());
    }
}
