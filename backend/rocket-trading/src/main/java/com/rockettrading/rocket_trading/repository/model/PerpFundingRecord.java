package com.rockettrading.rocket_trading.repository.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
public class PerpFundingRecord {
    private Long fundingId;
    private Long positionId;
    private Long clientId;
    private Long marketId;
    private String marketSymbol;
    private Instant fundingTime;
    private String positionSide;
    private BigDecimal size;
    private BigDecimal markPrice;
    private BigDecimal fundingRate;
    private BigDecimal payment;
}
