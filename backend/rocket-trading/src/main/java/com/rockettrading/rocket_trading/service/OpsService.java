package com.rockettrading.rocket_trading.service;

import com.rockettrading.rocket_trading.dto.ops.LedgerEntryResponse;
import com.rockettrading.rocket_trading.dto.ops.OpsOrderSummaryResponse;
import com.rockettrading.rocket_trading.dto.ops.OrderTraceResponse;
import com.rockettrading.rocket_trading.dto.order.FillResponse;
import com.rockettrading.rocket_trading.exception.NotFoundException;
import com.rockettrading.rocket_trading.repository.FillRepository;
import com.rockettrading.rocket_trading.repository.OpsRepository;
import com.rockettrading.rocket_trading.repository.TransactionRepository;
import com.rockettrading.rocket_trading.repository.model.OpsOrderSummaryRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class OpsService {
    private static final Set<String> STATUSES = Set.of("SUBMITTED", "ACCEPTED", "FILLED", "REJECTED", "CANCELLED");

    private final OpsRepository opsRepository;
    private final FillRepository fillRepository;
    private final TransactionRepository transactionRepository;
    private final OrderService orderService;

    public OpsService(OpsRepository opsRepository, FillRepository fillRepository,
                      TransactionRepository transactionRepository, OrderService orderService) {
        this.opsRepository = opsRepository;
        this.fillRepository = fillRepository;
        this.transactionRepository = transactionRepository;
        this.orderService = orderService;
    }

    @Transactional
    public List<OpsOrderSummaryResponse> search(long staffId, String query, String status, int limit) {
        Long orderId = null;
        String email = null;
        String symbol = null;
        String trimmed = query == null ? "" : query.trim();
        if (trimmed.matches("\\d{1,18}")) {
            orderId = Long.parseLong(trimmed);
        } else if (trimmed.contains("@")) {
            email = trimmed;
        } else if (!trimmed.isEmpty()) {
            symbol = trimmed.toUpperCase(Locale.ROOT);
        }
        String normalizedStatus = status == null || status.isBlank() ? null : status.trim().toUpperCase(Locale.ROOT);
        if (normalizedStatus != null && !STATUSES.contains(normalizedStatus)) {
            throw new IllegalArgumentException("status must be one of " + STATUSES);
        }
        opsRepository.logAccess(staffId, "order-search:" + (trimmed.isEmpty() ? "*" : trimmed)
                + (normalizedStatus == null ? "" : " status=" + normalizedStatus));
        return opsRepository.search(orderId, email, symbol, normalizedStatus, Math.max(1, Math.min(limit, 200))).stream()
                .map(OpsOrderSummaryResponse::from)
                .toList();
    }

    @Transactional
    public OrderTraceResponse trace(long staffId, long orderId) {
        List<OpsOrderSummaryRecord> matches = opsRepository.search(orderId, null, null, null, 1);
        if (matches.isEmpty()) {
            throw new NotFoundException("ORDER_NOT_FOUND", "No order exists with that reference");
        }
        opsRepository.logAccess(staffId, "order-trace:" + orderId);
        OpsOrderSummaryRecord order = matches.get(0);
        return new OrderTraceResponse(
                OpsOrderSummaryResponse.from(order),
                fillRepository.findByOrderId(orderId).stream().map(FillResponse::from).toList(),
                transactionRepository.findByOrderId(orderId).stream().map(LedgerEntryResponse::from).toList(),
                orderService.timelineFor(order.getClientId(), orderId).events()
        );
    }
}
