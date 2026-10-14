package com.rockettrading.rocket_trading.dto.order;

import java.util.List;

public record OrderTimelineResponse(
        long orderId,
        List<OrderTimelineEventResponse> events
) {
}
