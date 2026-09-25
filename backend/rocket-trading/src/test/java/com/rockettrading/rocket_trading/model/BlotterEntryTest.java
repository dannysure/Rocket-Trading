package com.rockettrading.rocket_trading.model;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class BlotterEntryTest {
    private BlotterEntry entry;
    private Instant now;
    private Instant later;

    @BeforeEach
    void setUp() {
        now = Instant.parse("2024-01-15T10:00:00Z");
        later = Instant.parse("2024-01-15T10:05:00Z");
    }

    @Test
    void testNoArgConstructor() {
        entry = new BlotterEntry();
        assertEquals(0, entry.getEntryId());
        assertEquals(0, entry.getOrderId());
        assertNull(entry.getSymbol());
        assertNull(entry.getSide());
        assertEquals(0, entry.getQuantity());
        assertNull(entry.getPrice());
        assertNull(entry.getDisplayStatus());
        assertNull(entry.getRecordedAt());
        assertNull(entry.getLastUpdatedAt());
    }

    @Test
    void testFullConstructor() {
        entry = new BlotterEntry(1, 100, "AAPL", "BUY", 50, new BigDecimal("150.25"), 
                                 "FILLED", now, later);
        assertEquals(1, entry.getEntryId());
        assertEquals(100, entry.getOrderId());
        assertEquals("AAPL", entry.getSymbol());
        assertEquals("BUY", entry.getSide());
        assertEquals(50, entry.getQuantity());
        assertEquals(new BigDecimal("150.25"), entry.getPrice());
        assertEquals("FILLED", entry.getDisplayStatus());
        assertEquals(now, entry.getRecordedAt());
        assertEquals(later, entry.getLastUpdatedAt());
    }

    @Test
    void testFullConstructorNormalizesSymbolToUppercase() {
        entry = new BlotterEntry(1, 100, "aapl", "BUY", 50, new BigDecimal("150.25"), 
                                 "FILLED", now, later);
        assertEquals("AAPL", entry.getSymbol());
    }

    @Test
    void testFullConstructorNormalizesSideToUppercase() {
        entry = new BlotterEntry(1, 100, "AAPL", "buy", 50, new BigDecimal("150.25"), 
                                 "FILLED", now, later);
        assertEquals("BUY", entry.getSide());
    }

    @Test
    void testSetEntryId() {
        entry = new BlotterEntry();
        entry.setEntryId(42);
        assertEquals(42, entry.getEntryId());
    }

    @Test
    void testSetEntryIdRejectsZero() {
        entry = new BlotterEntry();
        assertThrows(IllegalArgumentException.class, () -> entry.setEntryId(0));
    }

    @Test
    void testSetEntryIdRejectsNegative() {
        entry = new BlotterEntry();
        assertThrows(IllegalArgumentException.class, () -> entry.setEntryId(-1));
    }

    @Test
    void testSetOrderId() {
        entry = new BlotterEntry();
        entry.setOrderId(100);
        assertEquals(100, entry.getOrderId());
    }

    @Test
    void testSetOrderIdRejectsZero() {
        entry = new BlotterEntry();
        assertThrows(IllegalArgumentException.class, () -> entry.setOrderId(0));
    }

    @Test
    void testSetOrderIdRejectsNegative() {
        entry = new BlotterEntry();
        assertThrows(IllegalArgumentException.class, () -> entry.setOrderId(-5));
    }

    @Test
    void testSetSymbol() {
        entry = new BlotterEntry();
        entry.setSymbol("TSLA");
        assertEquals("TSLA", entry.getSymbol());
    }

    @Test
    void testSetSymbolNormalizesToUppercase() {
        entry = new BlotterEntry();
        entry.setSymbol("msft");
        assertEquals("MSFT", entry.getSymbol());
    }

    @Test
    void testSetSymbolTrimsWhitespace() {
        entry = new BlotterEntry();
        entry.setSymbol("  GOOG  ");
        assertEquals("GOOG", entry.getSymbol());
    }

    @Test
    void testSetSymbolRejectsNull() {
        entry = new BlotterEntry();
        assertThrows(IllegalArgumentException.class, () -> entry.setSymbol(null));
    }

    @Test
    void testSetSymbolRejectsBlank() {
        entry = new BlotterEntry();
        assertThrows(IllegalArgumentException.class, () -> entry.setSymbol(""));
        assertThrows(IllegalArgumentException.class, () -> entry.setSymbol("   "));
    }

    @Test
    void testSetSideBuy() {
        entry = new BlotterEntry();
        entry.setSide("BUY");
        assertEquals("BUY", entry.getSide());
    }

    @Test
    void testSetSideSell() {
        entry = new BlotterEntry();
        entry.setSide("SELL");
        assertEquals("SELL", entry.getSide());
    }

    @Test
    void testSetSideNormalizesToUppercase() {
        entry = new BlotterEntry();
        entry.setSide("sell");
        assertEquals("SELL", entry.getSide());
    }

    @Test
    void testSetSideTrimsWhitespace() {
        entry = new BlotterEntry();
        entry.setSide("  BUY  ");
        assertEquals("BUY", entry.getSide());
    }

    @Test
    void testSetSideRejectsInvalidValue() {
        entry = new BlotterEntry();
        assertThrows(IllegalArgumentException.class, () -> entry.setSide("HOLD"));
        assertThrows(IllegalArgumentException.class, () -> entry.setSide("SHORT"));
    }

    @Test
    void testSetSideRejectsNull() {
        entry = new BlotterEntry();
        assertThrows(IllegalArgumentException.class, () -> entry.setSide(null));
    }

    @Test
    void testSetSideRejectsBlank() {
        entry = new BlotterEntry();
        assertThrows(IllegalArgumentException.class, () -> entry.setSide(""));
        assertThrows(IllegalArgumentException.class, () -> entry.setSide("   "));
    }

    @Test
    void testSetQuantity() {
        entry = new BlotterEntry();
        entry.setQuantity(1000);
        assertEquals(1000, entry.getQuantity());
    }

    @Test
    void testSetQuantityRejectsZero() {
        entry = new BlotterEntry();
        assertThrows(IllegalArgumentException.class, () -> entry.setQuantity(0));
    }

    @Test
    void testSetQuantityRejectsNegative() {
        entry = new BlotterEntry();
        assertThrows(IllegalArgumentException.class, () -> entry.setQuantity(-100));
    }

    @Test
    void testSetPrice() {
        entry = new BlotterEntry();
        entry.setPrice(new BigDecimal("99.99"));
        assertEquals(new BigDecimal("99.99"), entry.getPrice());
    }

    @Test
    void testSetPriceFractionalCents() {
        entry = new BlotterEntry();
        entry.setPrice(new BigDecimal("150.251"));
        assertEquals(new BigDecimal("150.251"), entry.getPrice());
    }

    @Test
    void testSetPriceRejectsZero() {
        entry = new BlotterEntry();
        assertThrows(IllegalArgumentException.class, () -> entry.setPrice(BigDecimal.ZERO));
    }

    @Test
    void testSetPriceRejectsNegative() {
        entry = new BlotterEntry();
        assertThrows(IllegalArgumentException.class, () -> entry.setPrice(new BigDecimal("-50.00")));
    }

    @Test
    void testSetPriceRejectsNull() {
        entry = new BlotterEntry();
        assertThrows(IllegalArgumentException.class, () -> entry.setPrice(null));
    }

    @Test
    void testSetDisplayStatus() {
        entry = new BlotterEntry();
        entry.setDisplayStatus("PENDING");
        assertEquals("PENDING", entry.getDisplayStatus());
    }

    @Test
    void testSetDisplayStatusTrimsWhitespace() {
        entry = new BlotterEntry();
        entry.setDisplayStatus("  FILLED  ");
        assertEquals("FILLED", entry.getDisplayStatus());
    }

    @Test
    void testSetDisplayStatusRejectsNull() {
        entry = new BlotterEntry();
        assertThrows(IllegalArgumentException.class, () -> entry.setDisplayStatus(null));
    }

    @Test
    void testSetDisplayStatusRejectsBlank() {
        entry = new BlotterEntry();
        assertThrows(IllegalArgumentException.class, () -> entry.setDisplayStatus(""));
        assertThrows(IllegalArgumentException.class, () -> entry.setDisplayStatus("   "));
    }

    @Test
    void testSetRecordedAt() {
        entry = new BlotterEntry();
        entry.setRecordedAt(now);
        assertEquals(now, entry.getRecordedAt());
    }

    @Test
    void testSetRecordedAtRejectsNull() {
        entry = new BlotterEntry();
        assertThrows(IllegalArgumentException.class, () -> entry.setRecordedAt(null));
    }

    @Test
    void testSetLastUpdatedAt() {
        entry = new BlotterEntry();
        entry.setLastUpdatedAt(later);
        assertEquals(later, entry.getLastUpdatedAt());
    }

    @Test
    void testSetLastUpdatedAtRejectsNull() {
        entry = new BlotterEntry();
        assertThrows(IllegalArgumentException.class, () -> entry.setLastUpdatedAt(null));
    }

    @Test
    void testGetTotalValue() {
        entry = new BlotterEntry(1, 100, "AAPL", "BUY", 50, new BigDecimal("150.25"), 
                                 "FILLED", now, later);
        BigDecimal expected = new BigDecimal("150.25").multiply(BigDecimal.valueOf(50));
        assertEquals(expected, entry.getTotalValue());
    }

    @Test
    void testGetTotalValueWithLargeQuantity() {
        entry = new BlotterEntry(1, 100, "AAPL", "BUY", 10000, new BigDecimal("275.50"), 
                                 "FILLED", now, later);
        BigDecimal expected = new BigDecimal("275.50").multiply(BigDecimal.valueOf(10000));
        assertEquals(expected, entry.getTotalValue());
    }

    @Test
    void testGetTotalValueWithFractionalPrice() {
        entry = new BlotterEntry(1, 100, "AAPL", "BUY", 100, new BigDecimal("99.999"), 
                                 "FILLED", now, later);
        BigDecimal expected = new BigDecimal("99.999").multiply(BigDecimal.valueOf(100));
        assertEquals(expected, entry.getTotalValue());
    }

    @Test
    void testBuyAndSellScenarios() {
        BlotterEntry buyEntry = new BlotterEntry(1, 100, "MSFT", "BUY", 50, new BigDecimal("300.00"), 
                                                   "FILLED", now, later);
        BlotterEntry sellEntry = new BlotterEntry(2, 101, "MSFT", "SELL", 50, new BigDecimal("305.00"), 
                                                    "FILLED", now, later);

        assertEquals("BUY", buyEntry.getSide());
        assertEquals("SELL", sellEntry.getSide());
        assertEquals(new BigDecimal("15000.00"), buyEntry.getTotalValue());
        assertEquals(new BigDecimal("15250.00"), sellEntry.getTotalValue());
    }
}
