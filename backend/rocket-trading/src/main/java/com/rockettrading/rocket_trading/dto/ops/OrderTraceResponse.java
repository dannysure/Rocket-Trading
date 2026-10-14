package com.rockettrading.rocket_trading.dto.ops;

import com.rockettrading.rocket_trading.dto.order.FillResponse;
import com.rockettrading.rocket_trading.dto.order.OrderTimelineEventResponse;

import java.util.List;

/** Everything needed to answer a dispute about one trade, without reconstruction work (BR-15). */
public record OrderTraceResponse(
        OpsOrderSummaryResponse summary,
        List<FillResponse> fills,
        List<LedgerEntryResponse> ledger,
        List<OrderTimelineEventResponse> timeline
) {
}
