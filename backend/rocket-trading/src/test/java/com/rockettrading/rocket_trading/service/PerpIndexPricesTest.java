package com.rockettrading.rocket_trading.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rockettrading.rocket_trading.config.FauxnanceProperties;
import com.rockettrading.rocket_trading.config.PerpsProperties;
import com.rockettrading.rocket_trading.exception.ExternalServiceException;
import com.rockettrading.rocket_trading.model.Quote;
import com.rockettrading.rocket_trading.repository.PerpMarketRepository;
import com.rockettrading.rocket_trading.repository.model.PerpMarketRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PerpIndexPricesTest {
    private final PerpsProperties properties = new PerpsProperties();
    private final CoinbaseTickerFeed feed = new CoinbaseTickerFeed(mock(PerpMarketRepository.class), properties, new ObjectMapper());
    private final QuoteProvider provider = mock(QuoteProvider.class);
    private final QuoteService quoteService;
    private final PerpMarketRecord market = new PerpMarketRecord();

    PerpIndexPricesTest() {
        FauxnanceProperties fauxnance = new FauxnanceProperties();
        fauxnance.setMaxAgeSeconds(900);
        quoteService = new QuoteService(provider, fauxnance);
        market.setMarketSymbol("BTC-PERP");
        market.setUnderlyingSymbol("X:BTC-USD");
        market.setUnderlyingMarket("crypto");
    }

    @Test void usesTheLiveStreamWhenItHasACurrentPrice() {
        feed.handle("""
                {"type":"ticker","product_id":"BTC-USD","price":"81571.15","best_bid":"81571.14","best_ask":"81571.15","time":"%s"}
                """.formatted(Instant.now()));

        PerpIndexPrices.IndexQuote index = prices(feed).quote(market);

        assertTrue(index.live());
        assertEquals(new BigDecimal("81571.14"), index.quote().getBid());
        verifyNoInteractions(provider);
    }

    @Test void fallsBackToTheQuoteProviderAndReusesItsAnswerBriefly() {
        when(provider.fetchQuote("X:BTC-USD", "crypto")).thenReturn(quote("81000"));
        PerpIndexPrices prices = prices(feed);

        assertFalse(prices.quote(market).live());
        assertEquals(new BigDecimal("81000"), prices.quote(market).quote().getAsk());
        verify(provider, times(1)).fetchQuote("X:BTC-USD", "crypto");
    }

    @Test void fallbackFailuresAreAlsoReusedSoAnOutageCannotExhaustTheQuota() {
        when(provider.fetchQuote("X:BTC-USD", "crypto"))
                .thenThrow(new ExternalServiceException("PRICE_UNAVAILABLE", "quota exhausted"));
        PerpIndexPrices prices = prices(null);

        assertThrows(ExternalServiceException.class, () -> prices.quote(market));
        assertThrows(ExternalServiceException.class, () -> prices.quote(market));
        verify(provider, times(1)).fetchQuote("X:BTC-USD", "crypto");
    }

    private PerpIndexPrices prices(CoinbaseTickerFeed liveFeed) {
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        if (liveFeed != null) beans.addBean("feed", liveFeed);
        return new PerpIndexPrices(beans.getBeanProvider(CoinbaseTickerFeed.class), quoteService, properties);
    }

    private static Quote quote(String price) {
        BigDecimal value = new BigDecimal(price);
        return new Quote("X:BTC-USD", value, value, value, 0, 0, Instant.now());
    }
}
