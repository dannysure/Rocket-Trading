package com.rockettrading.rocket_trading.service;

import com.rockettrading.rocket_trading.config.FauxnanceProperties;
import com.rockettrading.rocket_trading.dto.quote.QuoteResponse;
import com.rockettrading.rocket_trading.exception.ConflictException;
import com.rockettrading.rocket_trading.model.Quote;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    }

    @Test
    void rejectsStaleQuoteFromProvider() {
        QuoteProvider quoteProvider = mock(QuoteProvider.class);
        FauxnanceProperties fauxnanceProperties = new FauxnanceProperties();
        fauxnanceProperties.setMaxAgeSeconds(1);
        QuoteService quoteService = new QuoteService(quoteProvider, fauxnanceProperties);
        Quote quote = new Quote("BTCUSD", new BigDecimal("64000.00"), new BigDecimal("64010.00"),
                new BigDecimal("64005.00"), 10, 12, Instant.now().minusSeconds(5));

        when(quoteProvider.fetchQuote("BTCUSD", "crypto")).thenReturn(quote);

        assertThrows(ConflictException.class, () -> quoteService.getQuote("BTCUSD", "crypto"));
    }
}
