package com.rockettrading.rocket_trading.model;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class PriceAlertTest {
    private PriceAlert alert;
    private Instant now;
    private Instant later;

    @BeforeEach
    void setUp() {
        now = Instant.parse("2024-01-15T10:00:00Z");
        later = Instant.parse("2024-01-15T10:05:00Z");
        
        alert = new PriceAlert(1, "AAPL", new BigDecimal("150.00"), "ABOVE", "ACTIVE", now);
    }

    @Test
    void testNoArgConstructor() {
        PriceAlert emptyAlert = new PriceAlert();
        assertEquals(0, emptyAlert.getAlertId());
        assertNull(emptyAlert.getSymbol());
        assertNull(emptyAlert.getTargetPrice());
        assertNull(emptyAlert.getDirection());
        assertNull(emptyAlert.getStatus());
        assertNull(emptyAlert.getCreatedAt());
        assertNull(emptyAlert.getTriggeredAt());
        assertNull(emptyAlert.getPriceAtTrigger());
    }

    @Test
    void testFullConstructor() {
        assertEquals(1, alert.getAlertId());
        assertEquals("AAPL", alert.getSymbol());
        assertEquals(new BigDecimal("150.00"), alert.getTargetPrice());
        assertEquals("ABOVE", alert.getDirection());
        assertEquals("ACTIVE", alert.getStatus());
        assertEquals(now, alert.getCreatedAt());
        assertNull(alert.getTriggeredAt());
        assertNull(alert.getPriceAtTrigger());
    }

    @Test
    void testFullConstructorNormalizesSymbol() {
        alert = new PriceAlert(2, "msft", new BigDecimal("300.00"), "BELOW", "ACTIVE", now);
        assertEquals("MSFT", alert.getSymbol());
    }

    @Test
    void testFullConstructorNormalizesDirection() {
        alert = new PriceAlert(3, "TSLA", new BigDecimal("250.00"), "above", "ACTIVE", now);
        assertEquals("ABOVE", alert.getDirection());
    }

    @Test
    void testFullConstructorNormalizesStatus() {
        alert = new PriceAlert(4, "GOOG", new BigDecimal("100.00"), "BELOW", "active", now);
        assertEquals("ACTIVE", alert.getStatus());
    }

    @Test
    void testSetAlertId() {
        alert.setAlertId(42);
        assertEquals(42, alert.getAlertId());
    }

    @Test
    void testSetAlertIdRejectsZero() {
        assertThrows(IllegalArgumentException.class, () -> alert.setAlertId(0));
    }

    @Test
    void testSetAlertIdRejectsNegative() {
        assertThrows(IllegalArgumentException.class, () -> alert.setAlertId(-1));
    }

    @Test
    void testSetSymbol() {
        alert.setSymbol("TSLA");
        assertEquals("TSLA", alert.getSymbol());
    }

    @Test
    void testSetSymbolNormalizesToUppercase() {
        alert.setSymbol("amzn");
        assertEquals("AMZN", alert.getSymbol());
    }

    @Test
    void testSetSymbolTrimsWhitespace() {
        alert.setSymbol("  NFLX  ");
        assertEquals("NFLX", alert.getSymbol());
    }

    @Test
    void testSetSymbolRejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> alert.setSymbol(null));
    }

    @Test
    void testSetSymbolRejectsBlank() {
        assertThrows(IllegalArgumentException.class, () -> alert.setSymbol(""));
        assertThrows(IllegalArgumentException.class, () -> alert.setSymbol("   "));
    }

    @Test
    void testSetTargetPrice() {
        alert.setTargetPrice(new BigDecimal("155.00"));
        assertEquals(new BigDecimal("155.00"), alert.getTargetPrice());
    }

    @Test
    void testSetTargetPriceRejectsZero() {
        assertThrows(IllegalArgumentException.class, () -> alert.setTargetPrice(BigDecimal.ZERO));
    }

    @Test
    void testSetTargetPriceRejectsNegative() {
        assertThrows(IllegalArgumentException.class, () -> alert.setTargetPrice(new BigDecimal("-50.00")));
    }

    @Test
    void testSetTargetPriceRejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> alert.setTargetPrice(null));
    }

    @Test
    void testSetDirectionAbove() {
        alert.setDirection("ABOVE");
        assertEquals("ABOVE", alert.getDirection());
    }

    @Test
    void testSetDirectionBelow() {
        alert.setDirection("BELOW");
        assertEquals("BELOW", alert.getDirection());
    }

    @Test
    void testSetDirectionNormalizesToUppercase() {
        alert.setDirection("above");
        assertEquals("ABOVE", alert.getDirection());
    }

    @Test
    void testSetDirectionTrimsWhitespace() {
        alert.setDirection("  BELOW  ");
        assertEquals("BELOW", alert.getDirection());
    }

    @Test
    void testSetDirectionRejectsInvalid() {
        assertThrows(IllegalArgumentException.class, () -> alert.setDirection("EQUAL"));
        assertThrows(IllegalArgumentException.class, () -> alert.setDirection("UP"));
        assertThrows(IllegalArgumentException.class, () -> alert.setDirection("DOWN"));
    }

    @Test
    void testSetDirectionRejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> alert.setDirection(null));
    }

    @Test
    void testSetDirectionRejectsBlank() {
        assertThrows(IllegalArgumentException.class, () -> alert.setDirection(""));
        assertThrows(IllegalArgumentException.class, () -> alert.setDirection("   "));
    }

    @Test
    void testSetStatusActive() {
        alert.setStatus("ACTIVE");
        assertEquals("ACTIVE", alert.getStatus());
    }

    @Test
    void testSetStatusTriggered() {
        alert.setStatus("TRIGGERED");
        assertEquals("TRIGGERED", alert.getStatus());
    }

    @Test
    void testSetStatusDismissed() {
        alert.setStatus("DISMISSED");
        assertEquals("DISMISSED", alert.getStatus());
    }

    @Test
    void testSetStatusExpired() {
        alert.setStatus("EXPIRED");
        assertEquals("EXPIRED", alert.getStatus());
    }

    @Test
    void testSetStatusNormalizesToUppercase() {
        alert.setStatus("active");
        assertEquals("ACTIVE", alert.getStatus());
    }

    @Test
    void testSetStatusTrimsWhitespace() {
        alert.setStatus("  TRIGGERED  ");
        assertEquals("TRIGGERED", alert.getStatus());
    }

    @Test
    void testSetStatusRejectsInvalid() {
        assertThrows(IllegalArgumentException.class, () -> alert.setStatus("PENDING"));
        assertThrows(IllegalArgumentException.class, () -> alert.setStatus("INACTIVE"));
        assertThrows(IllegalArgumentException.class, () -> alert.setStatus("CLOSED"));
    }

    @Test
    void testSetStatusRejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> alert.setStatus(null));
    }

    @Test
    void testSetStatusRejectsBlank() {
        assertThrows(IllegalArgumentException.class, () -> alert.setStatus(""));
        assertThrows(IllegalArgumentException.class, () -> alert.setStatus("   "));
    }

    @Test
    void testSetCreatedAt() {
        alert.setCreatedAt(later);
        assertEquals(later, alert.getCreatedAt());
    }

    @Test
    void testSetCreatedAtRejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> alert.setCreatedAt(null));
    }

    @Test
    void testSetTriggeredAt() {
        alert.setTriggeredAt(later);
        assertEquals(later, alert.getTriggeredAt());
    }

    @Test
    void testSetTriggeredAtRejectsBeforeCreatedAt() {
        Instant before = now.minusSeconds(10);
        assertThrows(IllegalArgumentException.class, () -> alert.setTriggeredAt(before));
    }

    @Test
    void testSetTriggeredAtAcceptsNull() {
        alert.setTriggeredAt(null);
        assertNull(alert.getTriggeredAt());
    }

    @Test
    void testSetPriceAtTrigger() {
        alert.setPriceAtTrigger(new BigDecimal("151.00"));
        assertEquals(new BigDecimal("151.00"), alert.getPriceAtTrigger());
    }

    @Test
    void testSetPriceAtTriggerRejectsZero() {
        assertThrows(IllegalArgumentException.class, () -> alert.setPriceAtTrigger(BigDecimal.ZERO));
    }

    @Test
    void testSetPriceAtTriggerRejectsNegative() {
        assertThrows(IllegalArgumentException.class, () -> alert.setPriceAtTrigger(new BigDecimal("-100.00")));
    }

    @Test
    void testSetPriceAtTriggerRejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> alert.setPriceAtTrigger(null));
    }

    @Test
    void testEvaluateAgainstQuoteAboveTriggered() {
        alert.setDirection("ABOVE");
        alert.setTargetPrice(new BigDecimal("150.00"));
        alert.setStatus("ACTIVE");
        
        assertTrue(alert.evaluateAgainstQuote(new BigDecimal("150.00")));
        assertTrue(alert.evaluateAgainstQuote(new BigDecimal("151.00")));
        assertTrue(alert.evaluateAgainstQuote(new BigDecimal("200.00")));
    }

    @Test
    void testEvaluateAgainstQuoteAboveNotTriggered() {
        alert.setDirection("ABOVE");
        alert.setTargetPrice(new BigDecimal("150.00"));
        alert.setStatus("ACTIVE");
        
        assertFalse(alert.evaluateAgainstQuote(new BigDecimal("149.99")));
        assertFalse(alert.evaluateAgainstQuote(new BigDecimal("100.00")));
    }

    @Test
    void testEvaluateAgainstQuoteBelowTriggered() {
        alert.setDirection("BELOW");
        alert.setTargetPrice(new BigDecimal("150.00"));
        alert.setStatus("ACTIVE");
        
        assertTrue(alert.evaluateAgainstQuote(new BigDecimal("150.00")));
        assertTrue(alert.evaluateAgainstQuote(new BigDecimal("149.99")));
        assertTrue(alert.evaluateAgainstQuote(new BigDecimal("100.00")));
    }

    @Test
    void testEvaluateAgainstQuoteBelowNotTriggered() {
        alert.setDirection("BELOW");
        alert.setTargetPrice(new BigDecimal("150.00"));
        alert.setStatus("ACTIVE");
        
        assertFalse(alert.evaluateAgainstQuote(new BigDecimal("150.01")));
        assertFalse(alert.evaluateAgainstQuote(new BigDecimal("200.00")));
    }

    @Test
    void testEvaluateAgainstQuoteInactiveAlert() {
        alert.setStatus("DISMISSED");
        assertFalse(alert.evaluateAgainstQuote(new BigDecimal("200.00")));
        
        alert.setStatus("TRIGGERED");
        assertFalse(alert.evaluateAgainstQuote(new BigDecimal("200.00")));
        
        alert.setStatus("EXPIRED");
        assertFalse(alert.evaluateAgainstQuote(new BigDecimal("200.00")));
    }

    @Test
    void testEvaluateAgainstQuoteRejectsNullPrice() {
        assertThrows(IllegalArgumentException.class, () -> alert.evaluateAgainstQuote(null));
    }

    @Test
    void testEvaluateAgainstQuoteRejectsNegativePrice() {
        assertThrows(IllegalArgumentException.class, () -> alert.evaluateAgainstQuote(new BigDecimal("-100.00")));
    }

    @Test
    void testTrigger() {
        alert.setStatus("ACTIVE");
        alert.trigger(new BigDecimal("151.00"), later);
        
        assertEquals("TRIGGERED", alert.getStatus());
        assertEquals(new BigDecimal("151.00"), alert.getPriceAtTrigger());
        assertEquals(later, alert.getTriggeredAt());
    }

    @Test
    void testTriggerRejectsNullPrice() {
        alert.setStatus("ACTIVE");
        assertThrows(IllegalArgumentException.class, () -> alert.trigger(null, later));
    }

    @Test
    void testTriggerRejectsNullInstant() {
        alert.setStatus("ACTIVE");
        assertThrows(IllegalArgumentException.class, () -> alert.trigger(new BigDecimal("151.00"), null));
    }

    @Test
    void testTriggerRejectsIfNotActive() {
        alert.setStatus("DISMISSED");
        assertThrows(IllegalStateException.class, () -> alert.trigger(new BigDecimal("151.00"), later));
        
        alert.setStatus("TRIGGERED");
        assertThrows(IllegalStateException.class, () -> alert.trigger(new BigDecimal("151.00"), later));
        
        alert.setStatus("EXPIRED");
        assertThrows(IllegalStateException.class, () -> alert.trigger(new BigDecimal("151.00"), later));
    }

    @Test
    void testDismiss() {
        alert.setStatus("ACTIVE");
        alert.dismiss();
        assertEquals("DISMISSED", alert.getStatus());
    }

    @Test
    void testDismissRejectsIfNotActive() {
        alert.setStatus("TRIGGERED");
        assertThrows(IllegalStateException.class, () -> alert.dismiss());
        
        alert.setStatus("DISMISSED");
        assertThrows(IllegalStateException.class, () -> alert.dismiss());
        
        alert.setStatus("EXPIRED");
        assertThrows(IllegalStateException.class, () -> alert.dismiss());
    }

    @Test
    void testExpire() {
        alert.setStatus("ACTIVE");
        alert.expire();
        assertEquals("EXPIRED", alert.getStatus());
    }

    @Test
    void testExpireRejectsIfTriggered() {
        alert.setStatus("TRIGGERED");
        assertThrows(IllegalStateException.class, () -> alert.expire());
    }

    @Test
    void testExpireAllowsFromActive() {
        alert.setStatus("ACTIVE");
        alert.expire();
        assertTrue(alert.isExpired());
    }

    @Test
    void testExpireAllowsFromDismissed() {
        alert.setStatus("DISMISSED");
        alert.expire();
        assertTrue(alert.isExpired());
    }

    @Test
    void testIsActive() {
        alert.setStatus("ACTIVE");
        assertTrue(alert.isActive());
        
        alert.setStatus("TRIGGERED");
        assertFalse(alert.isActive());
    }

    @Test
    void testIsTriggered() {
        alert.setStatus("TRIGGERED");
        assertTrue(alert.isTriggered());
        
        alert.setStatus("ACTIVE");
        assertFalse(alert.isTriggered());
    }

    @Test
    void testIsDismissed() {
        alert.setStatus("DISMISSED");
        assertTrue(alert.isDismissed());
        
        alert.setStatus("ACTIVE");
        assertFalse(alert.isDismissed());
    }

    @Test
    void testIsExpired() {
        alert.setStatus("EXPIRED");
        assertTrue(alert.isExpired());
        
        alert.setStatus("ACTIVE");
        assertFalse(alert.isExpired());
    }

    @Test
    void testCompleteAlertLifecycleAbove() {
        // Create alert: notify when AAPL >= 150
        PriceAlert aboveAlert = new PriceAlert(1, "AAPL", new BigDecimal("150.00"), "ABOVE", "ACTIVE", now);
        assertTrue(aboveAlert.isActive());
        
        // Price moves to 149.50 - no trigger
        assertFalse(aboveAlert.evaluateAgainstQuote(new BigDecimal("149.50")));
        
        // Price reaches 150.00 - trigger
        assertTrue(aboveAlert.evaluateAgainstQuote(new BigDecimal("150.00")));
        aboveAlert.trigger(new BigDecimal("150.00"), later);
        assertTrue(aboveAlert.isTriggered());
        assertEquals(new BigDecimal("150.00"), aboveAlert.getPriceAtTrigger());
    }

    @Test
    void testCompleteAlertLifecycleBelow() {
        // Create alert: notify when MSFT <= 300
        PriceAlert belowAlert = new PriceAlert(2, "MSFT", new BigDecimal("300.00"), "BELOW", "ACTIVE", now);
        assertTrue(belowAlert.isActive());
        
        // Price moves to 305.00 - no trigger
        assertFalse(belowAlert.evaluateAgainstQuote(new BigDecimal("305.00")));
        
        // Price drops to 299.99 - trigger
        assertTrue(belowAlert.evaluateAgainstQuote(new BigDecimal("299.99")));
        belowAlert.trigger(new BigDecimal("299.99"), later);
        assertTrue(belowAlert.isTriggered());
        assertEquals(new BigDecimal("299.99"), belowAlert.getPriceAtTrigger());
    }

    @Test
    void testAlertDismissal() {
        alert.setStatus("ACTIVE");
        assertTrue(alert.isActive());
        
        alert.dismiss();
        assertTrue(alert.isDismissed());
        
        // Dismissed alert no longer triggers
        assertFalse(alert.evaluateAgainstQuote(new BigDecimal("200.00")));
    }

    @Test
    void testAlertExpiration() {
        alert.setStatus("ACTIVE");
        alert.expire();
        assertTrue(alert.isExpired());
        
        // Expired alert no longer triggers
        assertFalse(alert.evaluateAgainstQuote(new BigDecimal("200.00")));
    }

    @Test
    void testMultipleAlertsForSameSymbol() {
        PriceAlert bullAlert = new PriceAlert(1, "AAPL", new BigDecimal("150.00"), "ABOVE", "ACTIVE", now);
        PriceAlert bearAlert = new PriceAlert(2, "AAPL", new BigDecimal("145.00"), "BELOW", "ACTIVE", now);
        
        // Price at 143.50: only bear alert would trigger (below 145)
        assertTrue(bearAlert.evaluateAgainstQuote(new BigDecimal("143.50")));
        assertFalse(bullAlert.evaluateAgainstQuote(new BigDecimal("143.50")));
        
        // Price at 152.00: only bull alert would trigger (above 150)
        assertTrue(bullAlert.evaluateAgainstQuote(new BigDecimal("152.00")));
        assertFalse(bearAlert.evaluateAgainstQuote(new BigDecimal("152.00")));
    }

    @Test
    void testAlertWithExactTargetPrice() {
        alert.setTargetPrice(new BigDecimal("150.00"));
        alert.setDirection("ABOVE");
        
        // Exactly at target should trigger for ABOVE
        assertTrue(alert.evaluateAgainstQuote(new BigDecimal("150.00")));
    }

    @Test
    void testAlertWithVeryPreciseTargetPrice() {
        alert.setTargetPrice(new BigDecimal("150.256"));
        alert.setDirection("ABOVE");
        
        assertTrue(alert.evaluateAgainstQuote(new BigDecimal("150.257")));
        assertFalse(alert.evaluateAgainstQuote(new BigDecimal("150.255")));
    }
}
