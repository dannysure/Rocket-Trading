package com.rockettrading.rocket_trading.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WatchlistTest {

    private Watchlist watchlist;
    private Instrument appleStock;
    private Instrument microsoftStock;
    private Instrument teslaStock;

    @BeforeEach
    void setUp() {
        watchlist = new Watchlist("Tech Stocks");
        appleStock = new Instrument("AAPL", "EQUITY", true);
        microsoftStock = new Instrument("MSFT", "EQUITY", true);
        teslaStock = new Instrument("TSLA", "EQUITY", true);
    }

    @Test
    @DisplayName("constructor stores watchlist name")
    void constructorStoresWatchlistName() {
        assertEquals("Tech Stocks", watchlist.getName());
    }

    @Test
    @DisplayName("constructor initializes empty instrument list")
    void constructorInitializesEmptyInstrumentList() {
        assertEquals(0, watchlist.getInstrumentCount());
    }

    @Test
    @DisplayName("addInstrument adds instrument to watchlist")
    void addInstrumentAddsInstrumentToWatchlist() {
        watchlist.addInstrument(appleStock);

        assertAll(
                () -> assertEquals(1, watchlist.getInstrumentCount()),
                () -> assertTrue(watchlist.contains(appleStock))
        );
    }

    @Test
    @DisplayName("addInstrument allows multiple different instruments")
    void addInstrumentAllowsMultipleDifferentInstruments() {
        watchlist.addInstrument(appleStock);
        watchlist.addInstrument(microsoftStock);
        watchlist.addInstrument(teslaStock);

        assertAll(
                () -> assertEquals(3, watchlist.getInstrumentCount()),
                () -> assertTrue(watchlist.contains(appleStock)),
                () -> assertTrue(watchlist.contains(microsoftStock)),
                () -> assertTrue(watchlist.contains(teslaStock))
        );
    }

    @Test
    @DisplayName("addInstrument rejects duplicate instruments")
    void addInstrumentRejectsDuplicateInstruments() {
        watchlist.addInstrument(appleStock);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> watchlist.addInstrument(appleStock)
        );

        assertEquals("instrument AAPL is already in the watchlist", exception.getMessage());
        assertEquals(1, watchlist.getInstrumentCount());
    }

    @Test
    @DisplayName("addInstrument rejects null instrument")
    void addInstrumentRejectsNullInstrument() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> watchlist.addInstrument(null)
        );

        assertEquals("instrument must not be null", exception.getMessage());
    }

    @Test
    @DisplayName("removeInstrument removes instrument from watchlist")
    void removeInstrumentRemovesInstrumentFromWatchlist() {
        watchlist.addInstrument(appleStock);
        watchlist.addInstrument(microsoftStock);

        watchlist.removeInstrument(appleStock);

        assertAll(
                () -> assertEquals(1, watchlist.getInstrumentCount()),
                () -> assertFalse(watchlist.contains(appleStock)),
                () -> assertTrue(watchlist.contains(microsoftStock))
        );
    }

    @Test
    @DisplayName("removeInstrument rejects instrument not in watchlist")
    void removeInstrumentRejectsInstrumentNotInWatchlist() {
        watchlist.addInstrument(appleStock);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> watchlist.removeInstrument(microsoftStock)
        );

        assertEquals("instrument MSFT is not in the watchlist", exception.getMessage());
    }

    @Test
    @DisplayName("removeInstrument rejects null instrument")
    void removeInstrumentRejectsNullInstrument() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> watchlist.removeInstrument(null)
        );

        assertEquals("instrument must not be null", exception.getMessage());
    }

    @Test
    @DisplayName("getInstruments returns copy of instrument list")
    void getInstrumentsReturnsCopyOfInstrumentList() {
        watchlist.addInstrument(appleStock);
        watchlist.addInstrument(microsoftStock);

        List<Instrument> retrieved = watchlist.getInstruments();

        assertAll(
                () -> assertEquals(2, retrieved.size()),
                () -> assertTrue(retrieved.contains(appleStock)),
                () -> assertTrue(retrieved.contains(microsoftStock))
        );
    }

    @Test
    @DisplayName("getInstruments returns independent copy")
    void getInstrumentsReturnsIndependentCopy() {
        watchlist.addInstrument(appleStock);

        List<Instrument> retrieved = watchlist.getInstruments();
        retrieved.add(microsoftStock);

        assertEquals(1, watchlist.getInstrumentCount());
    }

    @Test
    @DisplayName("contains returns true for instruments in watchlist")
    void containsReturnsTrueForInstrumentsInWatchlist() {
        watchlist.addInstrument(appleStock);

        assertTrue(watchlist.contains(appleStock));
    }

    @Test
    @DisplayName("contains returns false for instruments not in watchlist")
    void containsReturnsFalseForInstrumentsNotInWatchlist() {
        watchlist.addInstrument(appleStock);

        assertFalse(watchlist.contains(microsoftStock));
    }

    @Test
    @DisplayName("contains handles null gracefully")
    void containsHandlesNullGracefully() {
        assertFalse(watchlist.contains(null));
    }

    @Test
    @DisplayName("setName updates watchlist name")
    void setNameUpdatesWatchlistName() {
        watchlist.setName("Energy Stocks");

        assertEquals("Energy Stocks", watchlist.getName());
    }

    @Test
    @DisplayName("setName rejects blank name")
    void setNameRejectsBlankName() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> watchlist.setName("   ")
        );

        assertEquals("name must not be blank", exception.getMessage());
    }
}
