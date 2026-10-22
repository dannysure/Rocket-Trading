package com.rockettrading.rocket_trading.service;

/** Open interest in a market changed (a fill or a liquidation); published inside the changing transaction. */
public record PerpMarketChanged(String symbol) {
}
