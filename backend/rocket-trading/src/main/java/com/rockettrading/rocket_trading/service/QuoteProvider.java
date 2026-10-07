package com.rockettrading.rocket_trading.service;

import com.rockettrading.rocket_trading.model.Quote;
import com.rockettrading.rocket_trading.repository.model.FinancialInstrumentRecord;

public interface QuoteProvider {
    Quote fetchQuote(String symbol, String market);

    /** Looks up a symbol in the provider's registry; null when the provider does not know it. */
    default FinancialInstrumentRecord lookupInstrument(String symbol) {
        return null;
    }
}
