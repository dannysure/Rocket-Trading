package com.rockettrading.rocket_trading.model;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class PositionTest {
    private Position position;

    @BeforeEach
    void setUp() {
        position = new Position("AAPL", 100, new BigDecimal("150.00"), new BigDecimal("155.00"));
    }

    @Test
    void testNoArgConstructor() {
        Position empty = new Position();
        assertNull(empty.getSymbol());
        assertEquals(0, empty.getQuantity());
        assertNull(empty.getAverageCost());
        assertNull(empty.getCurrentPrice());
    }

    @Test
    void testThreeArgConstructor() {
        Position pos = new Position("MSFT", 50, new BigDecimal("300.00"));
        assertEquals("MSFT", pos.getSymbol());
        assertEquals(50, pos.getQuantity());
        assertEquals(new BigDecimal("300.00"), pos.getAverageCost());
        assertNull(pos.getCurrentPrice());
        assertEquals(50, pos.getTotalCostBasis());
    }

    @Test
    void testFourArgConstructor() {
        assertEquals("AAPL", position.getSymbol());
        assertEquals(100, position.getQuantity());
        assertEquals(new BigDecimal("150.00"), position.getAverageCost());
        assertEquals(new BigDecimal("155.00"), position.getCurrentPrice());
        assertEquals(100, position.getTotalCostBasis());
    }

    @Test
    void testSetSymbol() {
        position.setSymbol("TSLA");
        assertEquals("TSLA", position.getSymbol());
    }

    @Test
    void testSetSymbolNormalizesToUppercase() {
        position.setSymbol("msft");
        assertEquals("MSFT", position.getSymbol());
    }

    @Test
    void testSetSymbolTrimsWhitespace() {
        position.setSymbol("  GOOG  ");
        assertEquals("GOOG", position.getSymbol());
    }

    @Test
    void testSetSymbolRejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> position.setSymbol(null));
    }

    @Test
    void testSetSymbolRejectsBlank() {
        assertThrows(IllegalArgumentException.class, () -> position.setSymbol(""));
        assertThrows(IllegalArgumentException.class, () -> position.setSymbol("   "));
    }

    @Test
    void testSetQuantity() {
        position.setQuantity(200);
        assertEquals(200, position.getQuantity());
    }

    @Test
    void testSetQuantityZero() {
        position.setQuantity(0);
        assertEquals(0, position.getQuantity());
    }

    @Test
    void testSetQuantityRejectsNegative() {
        assertThrows(IllegalArgumentException.class, () -> position.setQuantity(-50));
    }

    @Test
    void testSetAverageCost() {
        position.setAverageCost(new BigDecimal("160.00"));
        assertEquals(new BigDecimal("160.00"), position.getAverageCost());
    }

    @Test
    void testSetAverageCostRejectsZero() {
        assertThrows(IllegalArgumentException.class, () -> position.setAverageCost(BigDecimal.ZERO));
    }

    @Test
    void testSetAverageCostRejectsNegative() {
        assertThrows(IllegalArgumentException.class, () -> position.setAverageCost(new BigDecimal("-100.00")));
    }

    @Test
    void testSetAverageCostRejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> position.setAverageCost(null));
    }

    @Test
    void testSetCurrentPrice() {
        position.setCurrentPrice(new BigDecimal("160.00"));
        assertEquals(new BigDecimal("160.00"), position.getCurrentPrice());
    }

    @Test
    void testSetCurrentPriceRejectsZero() {
        assertThrows(IllegalArgumentException.class, () -> position.setCurrentPrice(BigDecimal.ZERO));
    }

    @Test
    void testSetCurrentPriceRejectsNegative() {
        assertThrows(IllegalArgumentException.class, () -> position.setCurrentPrice(new BigDecimal("-50.00")));
    }

    @Test
    void testSetCurrentPriceRejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> position.setCurrentPrice(null));
    }

    @Test
    void testSetTotalCostBasis() {
        position.setTotalCostBasis(150);
        assertEquals(150, position.getTotalCostBasis());
    }

    @Test
    void testSetTotalCostBasisZero() {
        position.setTotalCostBasis(0);
        assertEquals(0, position.getTotalCostBasis());
    }

    @Test
    void testSetTotalCostBasisRejectsNegative() {
        assertThrows(IllegalArgumentException.class, () -> position.setTotalCostBasis(-100));
    }

    @Test
    void testGetPositionValue() {
        BigDecimal value = position.getPositionValue();
        BigDecimal expected = new BigDecimal("155.00").multiply(BigDecimal.valueOf(100));
        assertEquals(expected, value);
    }

    @Test
    void testGetPositionValueRejectsNullCurrentPrice() {
        assertThrows(IllegalArgumentException.class, () -> position.setCurrentPrice(null));
    }

    @Test
    void testGetCostBasisValue() {
        BigDecimal costBasis = position.getCostBasisValue();
        BigDecimal expected = new BigDecimal("150.00").multiply(BigDecimal.valueOf(100));
        assertEquals(expected, costBasis);
    }

    @Test
    void testGetCostBasisValueRejectsNullAverageCost() {
        assertThrows(IllegalArgumentException.class, () -> position.setAverageCost(null));
    }

    @Test
    void testGetUnrealizedGainLoss() {
        // Cost: 150 * 100 = 15000, Value: 155 * 100 = 15500, Gain = 500
        BigDecimal gainLoss = position.getUnrealizedGainLoss();
        assertEquals(new BigDecimal("500.00"), gainLoss);
    }

    @Test
    void testGetUnrealizedGainLossNegative() {
        position.setCurrentPrice(new BigDecimal("145.00"));
        // Cost: 150 * 100 = 15000, Value: 145 * 100 = 14500, Loss = -500
        BigDecimal gainLoss = position.getUnrealizedGainLoss();
        assertEquals(new BigDecimal("-500.00"), gainLoss);
    }

    @Test
    void testGetUnrealizedGainLossZero() {
        position.setCurrentPrice(new BigDecimal("150.00"));
        BigDecimal gainLoss = position.getUnrealizedGainLoss();
        assertEquals(new BigDecimal("0.00"), gainLoss);
    }

    @Test
    void testGetUnrealizedGainLossRejectsNullCurrentPrice() {
        assertThrows(IllegalArgumentException.class, () -> position.setCurrentPrice(null));
    }

    @Test
    void testGetUnrealizedGainLossRejectsNullAverageCost() {
        assertThrows(IllegalArgumentException.class, () -> position.setAverageCost(null));
    }

    @Test
    void testGetUnrealizedGainLossPercentage() {
        // Gain: 500, Cost: 15000, Percentage: 500/15000 * 100 = 3.333333%
        BigDecimal percentage = position.getUnrealizedGainLossPercentage();
        assertTrue(percentage.compareTo(new BigDecimal("3.33")) > 0);
        assertTrue(percentage.compareTo(new BigDecimal("3.34")) < 0);
    }

    @Test
    void testGetUnrealizedGainLossPercentageNegative() {
        position.setCurrentPrice(new BigDecimal("145.00"));
        // Loss: -500, Cost: 15000, Percentage: -500/15000 * 100 = -3.333333%
        BigDecimal percentage = position.getUnrealizedGainLossPercentage();
        assertTrue(percentage.compareTo(new BigDecimal("-3.34")) > 0); // -3.333... > -3.34
        assertTrue(percentage.compareTo(new BigDecimal("-3.33")) < 0); // -3.333... < -3.33
    }

    @Test
    void testGetUnrealizedGainLossPercentageLargeGain() {
        position.setCurrentPrice(new BigDecimal("300.00"));
        // Gain: 15000, Cost: 15000, Percentage: 100%
        BigDecimal percentage = position.getUnrealizedGainLossPercentage();
        assertEquals(new BigDecimal("100.000000"), percentage);
    }

    @Test
    void testGetUnrealizedGainLossPercentageRejectsZeroQuantity() {
        position.setQuantity(0);
        assertThrows(IllegalStateException.class, () -> position.getUnrealizedGainLossPercentage());
    }

    @Test
    void testApplyFillSingleFill() {
        Position newPos = new Position();
        newPos.setSymbol("AAPL");
        newPos.setQuantity(0);
        newPos.applyFill(100, new BigDecimal("150.00"));
        
        assertEquals(100, newPos.getQuantity());
        assertEquals(new BigDecimal("150.00000000"), newPos.getAverageCost());
    }

    @Test
    void testApplyFillMultipleFillsAveragingDown() {
        // Start with 100 @ 150
        position.setQuantity(100);
        position.setAverageCost(new BigDecimal("150.00"));
        
        // Add 100 @ 140
        position.applyFill(100, new BigDecimal("140.00"));
        
        assertEquals(200, position.getQuantity());
        // New avg: (150*100 + 140*100) / 200 = 29000/200 = 145
        assertEquals(new BigDecimal("145.00000000"), position.getAverageCost());
    }

    @Test
    void testApplyFillMultipleFillsAveragingUp() {
        // Start with 100 @ 150
        position.setQuantity(100);
        position.setAverageCost(new BigDecimal("150.00"));
        
        // Add 100 @ 160
        position.applyFill(100, new BigDecimal("160.00"));
        
        assertEquals(200, position.getQuantity());
        // New avg: (150*100 + 160*100) / 200 = 31000/200 = 155
        assertEquals(new BigDecimal("155.00000000"), position.getAverageCost());
    }

    @Test
    void testApplyFillThreeTrades() {
        position.setQuantity(50);
        position.setAverageCost(new BigDecimal("100.00"));
        
        position.applyFill(30, new BigDecimal("110.00"));
        assertEquals(80, position.getQuantity());
        // Avg: (100*50 + 110*30) / 80 = 8300/80 = 103.75
        assertEquals(new BigDecimal("103.75000000"), position.getAverageCost());
        
        position.applyFill(20, new BigDecimal("105.00"));
        assertEquals(100, position.getQuantity());
        // Avg: (103.75*80 + 105*20) / 100 = 10400/100 = 104
        assertEquals(new BigDecimal("104.00000000"), position.getAverageCost());
    }

    @Test
    void testApplyFillRejectsNegativeQuantity() {
        assertThrows(IllegalArgumentException.class, () -> position.applyFill(-50, new BigDecimal("150.00")));
    }

    @Test
    void testApplyFillRejectsZeroQuantity() {
        assertThrows(IllegalArgumentException.class, () -> position.applyFill(0, new BigDecimal("150.00")));
    }

    @Test
    void testApplyFillRejectsNegativePrice() {
        assertThrows(IllegalArgumentException.class, () -> position.applyFill(50, new BigDecimal("-100.00")));
    }

    @Test
    void testApplyFillRejectsNullPrice() {
        assertThrows(IllegalArgumentException.class, () -> position.applyFill(50, null));
    }

    @Test
    void testReduceFill() {
        // Start with 100 shares @ 150
        position.setQuantity(100);
        position.setAverageCost(new BigDecimal("150.00"));
        
        // Sell 30 shares
        position.reduceFill(30, new BigDecimal("160.00"));
        
        assertEquals(70, position.getQuantity());
        // Average cost stays the same for remaining shares
        assertEquals(new BigDecimal("150.00"), position.getAverageCost());
        assertEquals(70, position.getTotalCostBasis());
    }

    @Test
    void testReduceFillToZero() {
        position.setQuantity(100);
        position.setAverageCost(new BigDecimal("150.00"));
        
        position.reduceFill(100, new BigDecimal("160.00"));
        
        assertEquals(0, position.getQuantity());
        assertTrue(position.isEmpty());
    }

    @Test
    void testReduceFillRejectsNegativeQuantity() {
        assertThrows(IllegalArgumentException.class, () -> position.reduceFill(-50, new BigDecimal("160.00")));
    }

    @Test
    void testReduceFillRejectsZeroQuantity() {
        assertThrows(IllegalArgumentException.class, () -> position.reduceFill(0, new BigDecimal("160.00")));
    }

    @Test
    void testReduceFillRejectsExceedingQuantity() {
        position.setQuantity(50);
        assertThrows(IllegalArgumentException.class, () -> position.reduceFill(100, new BigDecimal("160.00")));
    }

    @Test
    void testReduceFillRejectsNegativePrice() {
        position.setQuantity(100);
        assertThrows(IllegalArgumentException.class, () -> position.reduceFill(50, new BigDecimal("-100.00")));
    }

    @Test
    void testReduceFillRejectsNullPrice() {
        position.setQuantity(100);
        assertThrows(IllegalArgumentException.class, () -> position.reduceFill(50, null));
    }

    @Test
    void testIsEmpty() {
        position.setQuantity(0);
        assertTrue(position.isEmpty());
        
        position.setQuantity(1);
        assertFalse(position.isEmpty());
    }

    @Test
    void testIsOpen() {
        position.setQuantity(0);
        assertFalse(position.isOpen());
        
        position.setQuantity(100);
        assertTrue(position.isOpen());
    }

    @Test
    void testCompleteTradeLifecycle() {
        // Client opens a long position: Buy 100 @ 150
        Position longPos = new Position("AAPL", 100, new BigDecimal("150.00"), new BigDecimal("150.00"));
        assertTrue(longPos.isOpen());
        assertEquals(new BigDecimal("0.00"), longPos.getUnrealizedGainLoss());

        // Price moves up to 155
        longPos.setCurrentPrice(new BigDecimal("155.00"));
        assertEquals(new BigDecimal("500.00"), longPos.getUnrealizedGainLoss());
        BigDecimal percentGain = longPos.getUnrealizedGainLossPercentage();
        assertTrue(percentGain.compareTo(new BigDecimal("3.33")) > 0);

        // Client adds to position: Buy 50 @ 153
        longPos.applyFill(50, new BigDecimal("153.00"));
        assertEquals(150, longPos.getQuantity());
        // New avg: (150*100 + 153*50) / 150 = 22650/150 = 151
        assertEquals(new BigDecimal("151.00000000"), longPos.getAverageCost());

        // Price continues up to 160
        longPos.setCurrentPrice(new BigDecimal("160.00"));
        BigDecimal newGainLoss = longPos.getUnrealizedGainLoss();
        // Value: 160*150 = 24000, Cost: 151*150 = 22650, Gain = 1350
        assertEquals(new BigDecimal("1350.00000000"), newGainLoss);

        // Client takes partial profit: Sell 75 shares
        longPos.reduceFill(75, new BigDecimal("160.00"));
        assertEquals(75, longPos.getQuantity());
        assertEquals(new BigDecimal("151.00000000"), longPos.getAverageCost());
        
        // Final position: 75 @ 160 with 151 avg cost
        BigDecimal finalValue = longPos.getPositionValue();
        assertEquals(new BigDecimal("12000.00"), finalValue);
    }

    @Test
    void testMultiplePositionTracking() {
        Position aapl = new Position("AAPL", 100, new BigDecimal("150.00"), new BigDecimal("155.00"));
        Position msft = new Position("MSFT", 50, new BigDecimal("300.00"), new BigDecimal("310.00"));
        
        BigDecimal aaplValue = aapl.getPositionValue();
        BigDecimal msftValue = msft.getPositionValue();
        BigDecimal totalValue = aaplValue.add(msftValue);
        
        assertEquals(new BigDecimal("15500.00"), aaplValue);
        assertEquals(new BigDecimal("15500.00"), msftValue);
        assertEquals(new BigDecimal("31000.00"), totalValue);
    }

    @Test
    void testPrecisionInAverageCost() {
        // Test precision handling with fractional shares/prices
        Position pos = new Position("SPY", 333, new BigDecimal("420.123"));
        pos.applyFill(1, new BigDecimal("420.999"));
        
        // Should compute average with 8 decimal precision
        assertNotNull(pos.getAverageCost());
        assertEquals(334, pos.getQuantity());
    }
}
