package com.rockettrading.rocket_trading.service;

import com.rockettrading.rocket_trading.dto.quote.CandleResponse;
import com.rockettrading.rocket_trading.model.Quote;
import com.rockettrading.rocket_trading.repository.model.FinancialInstrumentRecord;

import java.time.LocalDate;
import java.util.List;

public interface QuoteProvider {
    Quote fetchQuote(String symbol, String market);

    /** Looks up a symbol in the provider's registry; null when the provider does not know it. */
    default FinancialInstrumentRecord lookupInstrument(String symbol) {
        return null;
    }

    /** Daily price history from {@code from} to today, oldest first; empty when unavailable. */
    default List<CandleResponse> fetchDailyCandles(String symbol, LocalDate from) {
        return List.of();
    }
}
