package com.rockettrading.rocket_trading.repository.model;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class PerpMarketRecord {
    private Long marketId;
    private String marketSymbol;
    private String baseAsset;
    private String underlyingSymbol;
    private String underlyingMarket;
    private String quoteCurrency;
    private Integer maxLeverage;
    private BigDecimal takerFeeRate;
    private BigDecimal skewScale;
}
