package com.rockettrading.rocket_trading.dto.ops;

import com.rockettrading.rocket_trading.dto.order.OrderResponse;
import com.rockettrading.rocket_trading.repository.model.OpsOrderSummaryRecord;

public record OpsOrderSummaryResponse(long clientId, String clientName, String clientEmail, OrderResponse order) {
    public static OpsOrderSummaryResponse from(OpsOrderSummaryRecord record) {
        return new OpsOrderSummaryResponse(record.getClientId(), record.getClientName(), record.getClientEmail(), OrderResponse.from(record));
    }
}
