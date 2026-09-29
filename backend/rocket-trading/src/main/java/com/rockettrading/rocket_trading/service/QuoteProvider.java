package com.rockettrading.rocket_trading.service;

import com.rockettrading.rocket_trading.model.Quote;

public interface QuoteProvider {
    Quote fetchQuote(String symbol, String market);
}
