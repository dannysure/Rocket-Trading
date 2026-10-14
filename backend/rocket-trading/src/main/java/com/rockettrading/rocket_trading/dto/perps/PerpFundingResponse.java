package com.rockettrading.rocket_trading.dto.perps;

import com.rockettrading.rocket_trading.repository.model.PerpFundingRecord;

import java.math.BigDecimal;
import java.time.Instant;

/** payment is positive when the position paid funding and negative when it received it. */
public record PerpFundingResponse(
        long fundingId,
        String symbol,
        String side,
        BigDecimal size,
        BigDecimal markPrice,
        BigDecimal fundingRate,
        BigDecimal payment,
        Instant fundingTime
) {
    public static PerpFundingResponse from(PerpFundingRecord funding) {
        return new PerpFundingResponse(
                funding.getFundingId(),
                funding.getMarketSymbol(),
                funding.getPositionSide(),
                funding.getSize(),
                funding.getMarkPrice(),
                funding.getFundingRate(),
                funding.getPayment(),
                funding.getFundingTime()
        );
    }
}
