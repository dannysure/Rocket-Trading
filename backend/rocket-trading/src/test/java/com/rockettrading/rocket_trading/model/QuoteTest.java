package com.rockettrading.rocket_trading.model;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class QuoteTest {
    private Quote quote;
    private Instant now;
    private Instant later;
    private Instant muchLater;

    @BeforeEach
    void setUp() {
        now = Instant.parse("2024-01-15T10:00:00Z");
        later = Instant.parse("2024-01-15T10:00:30Z");
        muchLater = Instant.parse("2024-01-15T10:05:00Z");
        
        quote = new Quote("AAPL", new BigDecimal("150.00"), new BigDecimal("150.50"), 
                         new BigDecimal("150.25"), 1000, 1500, now);
    }

    @Test
    void testNoArgConstructor() {
        Quote emptyQuote = new Quote();
        assertNull(emptyQuote.getSymbol());
        assertNull(emptyQuote.getBid());
        assertNull(emptyQuote.getAsk());
        assertNull(emptyQuote.getPrice());
        assertEquals(0, emptyQuote.getBidVolume());
        assertEquals(0, emptyQuote.getAskVolume());
        assertNull(emptyQuote.getCapturedAt());
    }

    @Test
    void testFullConstructor() {
        assertEquals("AAPL", quote.getSymbol());
        assertEquals(new BigDecimal("150.00"), quote.getBid());
        assertEquals(new BigDecimal("150.50"), quote.getAsk());
        assertEquals(new BigDecimal("150.25"), quote.getPrice());
        assertEquals(1000, quote.getBidVolume());
        assertEquals(1500, quote.getAskVolume());
        assertEquals(now, quote.getCapturedAt());
    }

    @Test
    void testFullConstructorNormalizesSymbol() {
        quote = new Quote("aapl", new BigDecimal("150.00"), new BigDecimal("150.50"), 
                         new BigDecimal("150.25"), 1000, 1500, now);
        assertEquals("AAPL", quote.getSymbol());
    }

    @Test
    void testSetSymbol() {
        quote.setSymbol("TSLA");
        assertEquals("TSLA", quote.getSymbol());
    }

    @Test
    void testSetSymbolNormalizesToUppercase() {
        quote.setSymbol("msft");
        assertEquals("MSFT", quote.getSymbol());
    }

    @Test
    void testSetSymbolTrimsWhitespace() {
        quote.setSymbol("  GOOG  ");
        assertEquals("GOOG", quote.getSymbol());
    }

    @Test
    void testSetSymbolRejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> quote.setSymbol(null));
    }

    @Test
    void testSetSymbolRejectsBlank() {
        assertThrows(IllegalArgumentException.class, () -> quote.setSymbol(""));
        assertThrows(IllegalArgumentException.class, () -> quote.setSymbol("   "));
    }

    @Test
    void testSetBid() {
        quote.setBid(new BigDecimal("149.50"));
        assertEquals(new BigDecimal("149.50"), quote.getBid());
    }

    @Test
    void testSetBidRejectsZero() {
        assertThrows(IllegalArgumentException.class, () -> quote.setBid(BigDecimal.ZERO));
    }

    @Test
    void testSetBidRejectsNegative() {
        assertThrows(IllegalArgumentException.class, () -> quote.setBid(new BigDecimal("-100.00")));
    }

    @Test
    void testSetBidRejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> quote.setBid(null));
    }

    @Test
    void testSetAsk() {
        quote.setAsk(new BigDecimal("151.00"));
        assertEquals(new BigDecimal("151.00"), quote.getAsk());
    }

    @Test
    void testSetAskRejectsZero() {
        assertThrows(IllegalArgumentException.class, () -> quote.setAsk(BigDecimal.ZERO));
    }

    @Test
    void testSetAskRejectsNegative() {
        assertThrows(IllegalArgumentException.class, () -> quote.setAsk(new BigDecimal("-100.00")));
    }

    @Test
    void testSetAskRejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> quote.setAsk(null));
    }

    @Test
    void testSetPrice() {
        quote.setPrice(new BigDecimal("150.75"));
        assertEquals(new BigDecimal("150.75"), quote.getPrice());
    }

    @Test
    void testSetPriceRejectsZero() {
        assertThrows(IllegalArgumentException.class, () -> quote.setPrice(BigDecimal.ZERO));
    }

    @Test
    void testSetPriceRejectsNegative() {
        assertThrows(IllegalArgumentException.class, () -> quote.setPrice(new BigDecimal("-100.00")));
    }

    @Test
    void testSetPriceRejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> quote.setPrice(null));
    }

    @Test
    void testSetBidVolume() {
        quote.setBidVolume(5000);
        assertEquals(5000, quote.getBidVolume());
    }

    @Test
    void testSetBidVolumeZero() {
        quote.setBidVolume(0);
        assertEquals(0, quote.getBidVolume());
    }

    @Test
    void testSetBidVolumeRejectsNegative() {
        assertThrows(IllegalArgumentException.class, () -> quote.setBidVolume(-100));
    }

    @Test
    void testSetAskVolume() {
        quote.setAskVolume(2500);
        assertEquals(2500, quote.getAskVolume());
    }

    @Test
    void testSetAskVolumeZero() {
        quote.setAskVolume(0);
        assertEquals(0, quote.getAskVolume());
    }

    @Test
    void testSetAskVolumeRejectsNegative() {
        assertThrows(IllegalArgumentException.class, () -> quote.setAskVolume(-500));
    }

    @Test
    void testSetCapturedAt() {
        quote.setCapturedAt(later);
        assertEquals(later, quote.getCapturedAt());
    }

    @Test
    void testSetCapturedAtRejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> quote.setCapturedAt(null));
    }

    @Test
    void testGetSpread() {
        BigDecimal spread = quote.getSpread();
        assertEquals(new BigDecimal("0.50"), spread);
    }

    @Test
    void testGetSpreadLargeSpread() {
        quote = new Quote("AAPL", new BigDecimal("100.00"), new BigDecimal("102.00"), 
                         new BigDecimal("101.00"), 1000, 1500, now);
        BigDecimal spread = quote.getSpread();
        assertEquals(new BigDecimal("2.00"), spread);
    }

    @Test
    void testGetSpreadTinySpread() {
        quote = new Quote("AAPL", new BigDecimal("150.000"), new BigDecimal("150.001"), 
                         new BigDecimal("150.0005"), 1000, 1500, now);
        BigDecimal spread = quote.getSpread();
        assertEquals(new BigDecimal("0.001"), spread);
    }

    @Test
    void testGetSpreadRejectsNullBid() {
        assertThrows(IllegalArgumentException.class, () -> quote.setBid(null));
    }

    @Test
    void testGetSpreadRejectsNullAsk() {
        assertThrows(IllegalArgumentException.class, () -> quote.setAsk(null));
    }

    @Test
    void testGetSpreadPercentage() {
        // Spread = 0.50, Bid = 150.00, percentage = 0.50/150.00 * 100 = 0.333333%
        BigDecimal percentage = quote.getSpreadPercentage();
        assertTrue(percentage.compareTo(new BigDecimal("0.33")) > 0);
        assertTrue(percentage.compareTo(new BigDecimal("0.34")) < 0);
    }

    @Test
    void testGetSpreadPercentageWideSpread() {
        quote = new Quote("AAPL", new BigDecimal("100.00"), new BigDecimal("110.00"), 
                         new BigDecimal("105.00"), 1000, 1500, now);
        BigDecimal percentage = quote.getSpreadPercentage();
        // Spread = 10, Bid = 100, percentage = 10/100 * 100 = 10%
        assertEquals(new BigDecimal("10.000000"), percentage);
    }

    @Test
    void testGetSpreadPercentageTinySpread() {
        quote = new Quote("AAPL", new BigDecimal("150.00"), new BigDecimal("150.01"), 
                         new BigDecimal("150.005"), 1000, 1500, now);
        BigDecimal percentage = quote.getSpreadPercentage();
        // Spread = 0.01, Bid = 150, percentage = 0.01/150 * 100 = 0.00666%
        assertTrue(percentage.compareTo(BigDecimal.ZERO) > 0);
        assertTrue(percentage.compareTo(new BigDecimal("0.01")) < 0);
    }

    @Test
    void testGetSpreadPercentageRejectsNullBid() {
        assertThrows(IllegalArgumentException.class, () -> quote.setBid(null));
    }

    @Test
    void testGetSpreadPercentageRejectsNullAsk() {
        assertThrows(IllegalArgumentException.class, () -> quote.setAsk(null));
    }

    @Test
    void testIsCurrentWithinMaxAge() {
        assertTrue(quote.isCurrent(now, 30));
        assertTrue(quote.isCurrent(later, 30));
    }

    @Test
    void testIsCurrentExactlyAtMaxAge() {
        Instant atMaxAge = now.plus(Duration.ofSeconds(30));
        assertTrue(quote.isCurrent(atMaxAge, 30));
    }

    @Test
    void testIsCurrentBeyondMaxAge() {
        assertFalse(quote.isCurrent(muchLater, 30));
    }

    @Test
    void testIsCurrentWithLargeMaxAge() {
        assertTrue(quote.isCurrent(muchLater, 3600));
    }

    @Test
    void testIsCurrentWithMaxAgeOne() {
        Instant oneSecondAfter = now.plus(Duration.ofSeconds(1));
        assertTrue(quote.isCurrent(oneSecondAfter, 1));
    }

    @Test
    void testIsCurrentRejectsNullAsOf() {
        assertThrows(IllegalArgumentException.class, () -> quote.isCurrent(null, 30));
    }

    @Test
    void testIsCurrentRejectsZeroMaxAge() {
        assertThrows(IllegalArgumentException.class, () -> quote.isCurrent(now, 0));
    }

    @Test
    void testIsCurrentRejectsNegativeMaxAge() {
        assertThrows(IllegalArgumentException.class, () -> quote.isCurrent(now, -10));
    }

    @Test
    void testGetAgeInSeconds() {
        long age = quote.getAgeInSeconds(later);
        assertEquals(30, age);
    }

    @Test
    void testGetAgeInSecondsZero() {
        long age = quote.getAgeInSeconds(now);
        assertEquals(0, age);
    }

    @Test
    void testGetAgeInSecondsMuchLater() {
        long age = quote.getAgeInSeconds(muchLater);
        assertEquals(300, age);
    }

    @Test
    void testGetAgeInSecondsRejectsNullAsOf() {
        assertThrows(IllegalArgumentException.class, () -> quote.getAgeInSeconds(null));
    }

    @Test
    void testGetAgeInSecondsRejectsBeforeCapturedAt() {
        Instant before = now.minus(Duration.ofSeconds(10));
        assertThrows(IllegalArgumentException.class, () -> quote.getAgeInSeconds(before));
    }

    @Test
    void testIsStale() {
        assertFalse(quote.isStale(now, 30));
        assertFalse(quote.isStale(later, 30));
        assertTrue(quote.isStale(muchLater, 30));
    }

    @Test
    void testIsStaleAtExactMaxAge() {
        Instant atMaxAge = now.plus(Duration.ofSeconds(30));
        assertFalse(quote.isStale(atMaxAge, 30));
    }

    @Test
    void testIsStaleJustBeyondMaxAge() {
        Instant justBeyond = now.plus(Duration.ofSeconds(31));
        assertTrue(quote.isStale(justBeyond, 30));
    }

    @Test
    void testQuoteSnapshot() {
        // Verify complete quote capture
        assertEquals("AAPL", quote.getSymbol());
        assertEquals(new BigDecimal("150.00"), quote.getBid());
        assertEquals(new BigDecimal("150.50"), quote.getAsk());
        assertEquals(new BigDecimal("150.25"), quote.getPrice());
        assertEquals(1000, quote.getBidVolume());
        assertEquals(1500, quote.getAskVolume());
        assertEquals(now, quote.getCapturedAt());
    }

    @Test
    void testMultipleQuotesForSameSymbol() {
        Quote quote1 = new Quote("AAPL", new BigDecimal("150.00"), new BigDecimal("150.50"), 
                                 new BigDecimal("150.25"), 1000, 1500, now);
        Quote quote2 = new Quote("AAPL", new BigDecimal("150.25"), new BigDecimal("150.75"), 
                                 new BigDecimal("150.50"), 2000, 2500, later);

        // Price moved up from 150.25 to 150.50
        assertTrue(quote2.getPrice().compareTo(quote1.getPrice()) > 0);
        assertEquals(quote1.getSymbol(), quote2.getSymbol());
        // quote1 was captured 30 seconds ago (at now), check age at later time
        assertEquals(30, quote1.getAgeInSeconds(later));
        // quote2 was just captured, age is 0
        assertEquals(0, quote2.getAgeInSeconds(later));
    }

    @Test
    void testHighFrequencyQuoteFreshness() {
        // Simulate high-frequency trading: quotes updated every 100ms
        Instant time0 = Instant.parse("2024-01-15T10:00:00.000Z");
        Instant time1 = Instant.parse("2024-01-15T10:00:00.100Z");

        Quote q = new Quote("SPY", new BigDecimal("450.00"), new BigDecimal("450.10"), 
                           new BigDecimal("450.05"), 5000, 5000, time0);

        // Quote is fresh if within 1 second
        assertTrue(q.isCurrent(time1, 1)); // 100ms is within 1 second
    }

    @Test
    void testHighFrequencyQuoteStaleness() {
        Instant time0 = Instant.parse("2024-01-15T10:00:00.000Z");
        Instant time2 = Instant.parse("2024-01-15T10:00:01.500Z"); // 1.5 seconds later

        Quote q = new Quote("SPY", new BigDecimal("450.00"), new BigDecimal("450.10"), 
                           new BigDecimal("450.05"), 5000, 5000, time0);

        // Quote becomes stale after maxAgeSeconds
        assertFalse(q.isCurrent(time2, 1)); // 1.5 seconds exceeds 1 second limit
    }

    @Test
    void testQuoteStaleAfterAge() {
        Instant time0 = Instant.parse("2024-01-15T10:00:00.000Z");
        Instant time2 = Instant.parse("2024-01-15T10:00:01.500Z"); // 1.5 seconds later

        Quote q = new Quote("SPY", new BigDecimal("450.00"), new BigDecimal("450.10"), 
                           new BigDecimal("450.05"), 5000, 5000, time0);

        // Quote becomes stale after maxAgeSeconds
        assertFalse(q.isCurrent(time2, 1)); // 1.5 seconds exceeds 1 second limit
    }

    @Test
    void testBidAskImbalance() {
        // Large bid/ask volume imbalance
        Quote imbalanced = new Quote("XYZ", new BigDecimal("100.00"), new BigDecimal("100.50"), 
                                     new BigDecimal("100.25"), 100, 50000, now);
        assertEquals(100, imbalanced.getBidVolume());
        assertEquals(50000, imbalanced.getAskVolume());
    }

    @Test
    void testPriceMovement() {
        Quote oldQuote = new Quote("AAPL", new BigDecimal("150.00"), new BigDecimal("150.50"), 
                                   new BigDecimal("150.25"), 1000, 1500, now);
        Quote newQuote = new Quote("AAPL", new BigDecimal("151.00"), new BigDecimal("151.50"), 
                                   new BigDecimal("151.25"), 1000, 1500, later);

        BigDecimal priceChange = newQuote.getPrice().subtract(oldQuote.getPrice());
        assertEquals(new BigDecimal("1.00"), priceChange);
    }
}
