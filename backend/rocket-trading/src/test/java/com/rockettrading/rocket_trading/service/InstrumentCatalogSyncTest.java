package com.rockettrading.rocket_trading.service;

import com.rockettrading.rocket_trading.exception.ExternalServiceException;
import com.rockettrading.rocket_trading.model.Quote;
import com.rockettrading.rocket_trading.repository.InstrumentRepository;
import com.rockettrading.rocket_trading.repository.model.FinancialInstrumentRecord;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

class InstrumentCatalogSyncTest {

    @Test
    void catalogueTakesEveryDetailFromTheProviderAndWithdrawsUnknownTickers() {
        InstrumentRepository repository = mock(InstrumentRepository.class);
        when(repository.findAll()).thenReturn(List.of(
                instrument(1, "NVDA", "NVIDIA Corporation", "Equity", "USD", "US", "NASDAQ"),
                instrument(2, "BP", "BP p.l.c. (US listing)", "Equity", "USD", "UK", "NYSE"),
                instrument(3, "INFY.NS", "Infosys", "Equity", "INR", "US", null),
                instrument(4, "GONE", "Delisted Co", "Equity", "USD", "US", "NYSE"),
                instrument(5, "AAPL", "Apple Inc.", "Equity", "USD", "US", "US"),
                instrument(6, "MSFT", "Microsoft", "Equity", "USD", "US", "US")));

        Map<String, FinancialInstrumentRecord> provider = Map.of(
                "NVDA", instrument(0, "NVDA", "Nvidia", "Equity", "USD", "US", "US"),
                "BP", instrument(0, "BP", "BP p.l.c.", "Equity", "USD", "US", "NYSE"),
                "INFY.NS", instrument(0, "INFY.NS", "Infosys Limited", "Equity", "INR", "IN", "NSE"),
                "AAPL", instrument(0, "AAPL", "Apple Inc.", "Equity", "USD", "US", "US"));
        QuoteProvider fauxnance = new QuoteProvider() {
            public Quote fetchQuote(String symbol, String market) { throw new UnsupportedOperationException(); }
            public FinancialInstrumentRecord lookupInstrument(String symbol) {
                if ("MSFT".equals(symbol)) throw new ExternalServiceException("SYMBOL_LOOKUP_FAILED", "provider down");
                return provider.get(symbol);
            }
        };

        var result = new InstrumentCatalogSync(repository, fauxnance).syncAll();

        // Name and exchange come from Fauxnance; the platform's UK grouping for a US listing is kept
        verify(repository).updateFromProvider(argThat(i -> i.getInstrumentId() == 1 && "Nvidia".equals(i.getInstrumentName()) && "US".equals(i.getListingExchange())));
        verify(repository).updateFromProvider(argThat(i -> i.getInstrumentId() == 2 && "BP p.l.c.".equals(i.getInstrumentName()) && "UK".equals(i.getMarketSegment())));
        // Fauxnance says NSE, so it is an Indian equity whatever was stored before
        verify(repository).updateFromProvider(argThat(i -> i.getInstrumentId() == 3 && "IN".equals(i.getMarketSegment())));
        // Unknown to Fauxnance: no longer offered. Unchanged or unreachable: left alone.
        verify(repository).markNotTradable(4);
        verify(repository, times(3)).updateFromProvider(any());
        assertEquals(new InstrumentCatalogSync.Result(3, 1, 1), result);
    }

    private FinancialInstrumentRecord instrument(long id, String symbol, String name, String assetClass,
                                                 String currency, String segment, String exchange) {
        FinancialInstrumentRecord record = new FinancialInstrumentRecord();
        record.setInstrumentId(id);
        record.setTickerSymbol(symbol);
        record.setInstrumentName(name);
        record.setAssetClass(assetClass);
        record.setBaseCurrency(currency);
        record.setTradable(true);
        record.setMarketSegment(segment);
        record.setListingExchange(exchange);
        return record;
    }
}
