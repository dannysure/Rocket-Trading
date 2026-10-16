package com.rockettrading.rocket_trading.repository.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PerpOpenInterestRecord {
    private Long marketId;
    private BigDecimal longSize = BigDecimal.ZERO;
    private BigDecimal shortSize = BigDecimal.ZERO;

    public static PerpOpenInterestRecord none(long marketId) {
        return new PerpOpenInterestRecord(marketId, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    /** Net long size: positive when longs outweigh shorts. */
    public BigDecimal skew() {
        return longSize.subtract(shortSize);
    }

    public BigDecimal total() {
        return longSize.add(shortSize);
    }
}
