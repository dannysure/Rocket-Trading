package com.rockettrading.rocket_trading.service;

import com.rockettrading.rocket_trading.config.FauxnanceProperties;
import com.rockettrading.rocket_trading.dto.quote.QuoteResponse;
import com.rockettrading.rocket_trading.exception.ConflictException;
import com.rockettrading.rocket_trading.model.Quote;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QuoteServiceLayerTest {

    @Test
    void returnsFreshQuoteFromProvider() {
        QuoteProvider quoteProvider = mock(QuoteProvider.class);
        FauxnanceProperties fauxnanceProperties = new FauxnanceProperties();
        fauxnanceProperties.setMaxAgeSeconds(60);
        QuoteService quoteService = new QuoteService(quoteProvider, fauxnanceProperties);
        Quote quote = new Quote("AAPL", new BigDecimal("100.00"), new BigDecimal("100.20"),
                new BigDecimal("100.10"), 1000, 1200, Instant.now());

        when(quoteProvider.fetchQuote("AAPL", "stock")).thenReturn(quote);

        QuoteResponse response = quoteService.getQuote("AAPL", "stock");

        assertEquals("AAPL", response.symbol());
        assertEquals("stock", response.market());
        assertTrue(response.current());
    }

    @Test
    void showsStaleQuoteAsNotCurrentButRefusesItForPricing() {
        QuoteProvider quoteProvider = mock(QuoteProvider.class);
        FauxnanceProperties fauxnanceProperties = new FauxnanceProperties();
        fauxnanceProperties.setMaxAgeSeconds(1);
        QuoteService quoteService = new QuoteService(quoteProvider, fauxnanceProperties);
        Quote quote = new Quote("BTCUSD", new BigDecimal("64000.00"), new BigDecimal("64010.00"),
                new BigDecimal("64005.00"), 10, 12, Instant.now().minusSeconds(5));

        when(quoteProvider.fetchQuote("BTCUSD", "crypto")).thenReturn(quote);

        // A closed market still shows its last price, flagged as not current
        assertFalse(quoteService.getQuote("BTCUSD", "crypto").current());
        ConflictException stale = assertThrows(ConflictException.class, () -> quoteService.fetchCurrentQuote("BTCUSD", "crypto"));
        assertEquals("QUOTE_STALE", stale.getCode());
    }

    @Test
    void fxQuotesUseTheirOwnDailyFreshnessWindow() {
        FauxnanceProperties properties = new FauxnanceProperties();
        properties.setMaxAgeSeconds(900);
        properties.setFxMaxAgeSeconds(345600);
        Quote dailyClose = new Quote("FX:GBPUSD", new BigDecimal("1.3241"), new BigDecimal("1.3243"),
                new BigDecimal("1.3242"), 0, 0, Instant.now().minusSeconds(18 * 3600));
        QuoteService quoteService = new QuoteService((symbol, market) -> dailyClose, properties);

        assertEquals("FX:GBPUSD", quoteService.fetchCurrentQuote("FX:GBPUSD", "fx").getSymbol());
        assertThrows(ConflictException.class, () -> quoteService.fetchCurrentQuote("FX:GBPUSD", "stock"));
    }

    @Test
    void derivesMarketFromCanonicalSymbol() {
        assertEquals("crypto", QuoteService.marketFor("X:BTC-USD"));
        assertEquals("fx", QuoteService.marketFor("fx:eurusd"));
        assertEquals("stock", QuoteService.marketFor("RELIANCE.NS"));
    }

    @Test
    void rejectsFutureWrongSymbolCrossedAndUnrepresentableQuotes() {
        FauxnanceProperties properties = new FauxnanceProperties();
        properties.setMaxAgeSeconds(60);
        for (Quote quote : java.util.List.of(
                new Quote("AAPL", BigDecimal.ONE, BigDecimal.TEN, BigDecimal.TEN, 0, 0, Instant.now().plusSeconds(60)),
                new Quote("MSFT", BigDecimal.ONE, BigDecimal.TEN, BigDecimal.TEN, 0, 0, Instant.now()),
                new Quote("AAPL", BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ONE, 0, 0, Instant.now()),
                new Quote("AAPL", new BigDecimal("0.000001"), BigDecimal.ONE, BigDecimal.ONE, 0, 0, Instant.now()))) {
            QuoteProvider provider = (symbol, market) -> quote;
            assertThrows(ConflictException.class, () -> new QuoteService(provider, properties).getQuote("AAPL", "stock"));
        }
    }
}
