package com.rockettrading.rocket_trading.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rockettrading.rocket_trading.dto.event.OrderAcceptedEvent;
import com.rockettrading.rocket_trading.dto.event.OrderFilledEvent;
import com.rockettrading.rocket_trading.dto.event.OrderRejectedEvent;
import com.rockettrading.rocket_trading.dto.order.*;
import com.rockettrading.rocket_trading.exception.*;
import com.rockettrading.rocket_trading.repository.*;
import com.rockettrading.rocket_trading.repository.model.*;
import com.rockettrading.rocket_trading.service.PricingService.Pricing;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OrderService {
    private final ClientAccountRepository clientAccountRepository;
    private final InstrumentRepository instrumentRepository;
    private final HoldingRepository holdingRepository;
    private final MarketQuoteRepository marketQuoteRepository;
    private final OrderRepository orderRepository;
    private final FillRepository fillRepository;
    private final TransactionRepository transactionRepository;
    private final AuditLogRepository auditLogRepository;
    private final PricingService pricingService;
    private final QuoteProvider quoteProvider;
    private final Validator validator;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<OrderEventProducer> eventProducer;
    private final ObjectProvider<OrderCacheService> orderCacheService;
    private final ObjectProvider<RateLimitService> rateLimitService;

    // How long to wait between attempts when a market has no current price, and when to give up
    @Value("${trading.deferral.retry-seconds:300}")
    private long deferralRetrySeconds = 300;
    @Value("${trading.deferral.max-hours:96}")
    private long deferralMaxHours = 96;

    // Return business rejections normally so their records commit. The controller chooses HTTP 409.
    @Transactional
    public OrderResponse submitOrder(long clientId, SubmitOrderRequest request, String key) {
        var violations = validator.validate(request);
        if (!violations.isEmpty()) throw new IllegalArgumentException(violations.iterator().next().getMessage());
        if (key == null || !key.matches("[A-Za-z0-9._:-]{1,100}")) {
            throw new IllegalArgumentException("Idempotency-Key is required (1-100 letters, digits, '.', '_', ':', '-')");
        }
        // Check rate limit (100 orders per minute)
        RateLimitService rateLimiter = rateLimitService.getIfAvailable();
        if (rateLimiter != null && !rateLimiter.isAllowed(clientId)) {
            throw new IllegalArgumentException("Rate limit exceeded: maximum 100 orders per minute");
        }
        // One account lock serializes same-client submissions and concurrent settlement.
        ClientAccountRecord account = requireLockedAccount(clientId);
        OrderRecord existing = orderRepository.findByIdempotencyKey(clientId, key);
        if (existing != null) {
            if (!request.fingerprint().equals(existing.getRequestFingerprint())) {
                throw new ConflictException("IDEMPOTENCY_CONFLICT", "Key was already used for different order details");
            }
            return getOrder(clientId, existing.getOrderId());
        }
        FinancialInstrumentRecord instrument = findOrRegisterInstrument(request.symbol());
        if (instrument == null) throw new NotFoundException("INSTRUMENT_NOT_FOUND", "Symbol is not recognized by the quote provider");

        OrderRecord order = new OrderRecord();
        order.setClientId(clientId);
        order.setAccountId(account.getAccountId());
        order.setInstrumentId(instrument.getInstrumentId());
        order.setSymbol(instrument.getTickerSymbol());
        order.setMarket(request.market());
        order.setOrderSide(request.side());
        order.setOrderType(request.orderType());
        order.setRequestedQuantity(request.quantity());
        order.setLimitPrice(request.limitPrice());
        order.setIdempotencyKey(key);
        order.setRequestFingerprint(request.fingerprint());
        order.setOrderStatus("SUBMITTED");
        orderRepository.insert(order);
        audit("orders", order.getOrderId(), clientId, null, Map.of("status", "SUBMITTED", "request", request));
        try {
            validateInstrument(order, instrument);
            // A closed market still has a last price: checks run against it and execution waits for a current one
            Pricing pricing = pricingService.priceIndicative(instrument, order.getOrderSide(), order.getRequestedQuantity(), account.getCurrency());
            recordPricing(order, pricing, "acceptance");
            validateBalances(order, account, pricing);
            transition(order, "ACCEPTED", null);
        } catch (ApiException | IllegalArgumentException rejection) {
            transition(order, "REJECTED", rejection.getMessage());
        }
        return getOrder(clientId, order.getOrderId());
    }

    // Called through Spring's transaction proxy by the worker, AFTER acceptance has committed.
    @Transactional
    public void executeOrder(long orderId) {
        OrderRecord order = orderRepository.lockAcceptedOrder(orderId);
        if (order == null) return; // Already settled or another worker owns it.
        ClientAccountRecord account = requireLockedAccount(order.getClientId());
        FinancialInstrumentRecord instrument = instrumentRepository.findByTicker(order.getSymbol());
        Pricing pricing;
        try {
            validateInstrument(order, instrument);
            pricing = pricingService.priceForExecution(instrument, order.getOrderSide(), order.getRequestedQuantity(), account.getCurrency());
        } catch (ApiException priceProblem) {
            if (isAwaitingPrice(priceProblem)) {
                defer(order, priceProblem.getMessage());
            } else {
                transition(order, "REJECTED", priceProblem.getMessage());
            }
            return;
        } catch (IllegalArgumentException rejection) {
            transition(order, "REJECTED", rejection.getMessage());
            return;
        }
        recordPricing(order, pricing, "execution");
        try {
            validateBalances(order, account, pricing);
        } catch (IllegalArgumentException rejection) {
            transition(order, "REJECTED", rejection.getMessage());
            return;
        }
        // Database failures escape and roll back this entire transaction. ACCEPTED survives for retry.
        MarketQuoteRecord snapshot = new MarketQuoteRecord();
        snapshot.setInstrumentId(order.getInstrumentId());
        snapshot.setBidPrice(pricing.quote().getBid());
        snapshot.setAskPrice(pricing.quote().getAsk());
        snapshot.setQuoteTimestamp(pricing.quote().getCapturedAt());
        marketQuoteRepository.insert(snapshot);
        applyFill(order, account, pricing, snapshot.getQuoteId());
    }

    public IndicativePriceResponse indicativePrice(long clientId, String symbol, String side, BigDecimal quantity) {
        if (!"BUY".equals(side) && !"SELL".equals(side)) throw new IllegalArgumentException("side must be BUY or SELL");
        if (quantity == null || quantity.signum() <= 0) throw new IllegalArgumentException("quantity must be positive");
        ClientAccountRecord account = clientAccountRepository.findDirectTradingAccountByClientId(clientId);
        if (account == null) throw new NotFoundException("ACCOUNT_NOT_FOUND", "No direct trading account exists for the signed-in client");
        FinancialInstrumentRecord instrument = findOrRegisterInstrument(symbol.trim().toUpperCase());
        if (instrument == null) throw new NotFoundException("INSTRUMENT_NOT_FOUND", "Symbol is not recognized by the quote provider");
        Pricing pricing = pricingService.priceIndicative(instrument, side, quantity, account.getCurrency());
        return new IndicativePriceResponse(
                instrument.getTickerSymbol(),
                instrument.getInstrumentName(),
                side,
                quantity,
                pricing.unitPrice(),
                pricing.instrumentCurrency(),
                pricing.fxRate(),
                pricing.settlementCurrency(),
                pricing.settlementAmount(),
                account.getCashBalance(),
                holdingQuantity(account.getAccountId(), instrument.getInstrumentId()),
                pricing.quote().getCapturedAt(),
                pricing.current()
        );
    }

    public List<OrderResponse> listOrders(long clientId) {
        return orderRepository.findByClientId(clientId).stream().map(OrderResponse::from).toList();
    }

    /** The signed-in client's own order; this is also the ownership check for fills and the timeline (BR-02). */
    public OrderResponse getOrder(long clientId, long orderId) {
        OrderCacheService cache = orderCacheService.getIfAvailable();
        OrderResponse cached = cache == null ? null : cache.getOrder(clientId, orderId);
        if (cached != null) {
            return cached;
        }
        OrderRecord order = orderRepository.findByIdAndClientId(orderId, clientId);
        if (order == null) throw new NotFoundException("ORDER_NOT_FOUND", "Order was not found for the signed-in client");
        OrderResponse response = OrderResponse.from(order);
        if (cache != null) {
            cache.cacheOrder(clientId, response);
        }
        return response;
    }

    public List<FillResponse> listFills(long clientId, long orderId) {
        getOrder(clientId, orderId);
        return fillRepository.findByOrderId(orderId).stream().map(FillResponse::from).toList();
    }

    public OrderTimelineResponse getOrderTimeline(long clientId, long orderId) {
        getOrder(clientId, orderId);
        return timelineFor(clientId, orderId);
    }

    /** Full lifecycle of one order, unscoped by client; callers must enforce who may see it. */
    OrderTimelineResponse timelineFor(long clientId, long orderId) {
        return new OrderTimelineResponse(
                orderId,
                auditLogRepository.findTimelineForOrder(clientId, orderId).stream()
                        .map(record -> OrderTimelineEventResponse.from(record, objectMapper))
                        .toList()
        );
    }

    private void applyFill(OrderRecord order, ClientAccountRecord account, Pricing pricing, long quoteId) {
        boolean buying = "BUY".equals(order.getOrderSide());
        BigDecimal quantity = order.getRequestedQuantity();
        BigDecimal settlement = pricing.settlementAmount();

        FillRecord fill = new FillRecord();
        fill.setOrderId(order.getOrderId());
        fill.setExecutedQuantity(quantity);
        fill.setExecutedPrice(pricing.unitPrice());
        fill.setQuoteId(quoteId);
        fill.setExecutedAt(Instant.now());
        fill.setInstrumentCurrency(pricing.instrumentCurrency());
        fill.setSettlementCurrency(pricing.settlementCurrency());
        fill.setFxRate(pricing.fxRate());
        fill.setSettlementAmount(settlement);
        fillRepository.insert(fill);

        BigDecimal newCash = buying ? account.getCashBalance().subtract(settlement) : account.getCashBalance().add(settlement);
        clientAccountRepository.updateCashBalance(account.getAccountId(), newCash);

        HoldingRecord before = holdingRepository.findAccountHolding(account.getAccountId(), order.getInstrumentId());
        BigDecimal beforeQuantity = before == null ? BigDecimal.ZERO : before.getQuantity();
        BigDecimal beforeAverage = before == null ? null : before.getAverageCost();
        BigDecimal afterQuantity = buying ? beforeQuantity.add(quantity) : beforeQuantity.subtract(quantity);
        BigDecimal afterAverage = buying ? averageCostAfterBuy(beforeQuantity, beforeAverage, quantity, settlement) : beforeAverage;
        holdingRepository.upsertAccountHolding(account.getAccountId(), order.getInstrumentId(), afterQuantity, afterAverage);
        // This phase has exactly one DIRECT_TRADING account per client; retain both existing read models.
        holdingRepository.upsertClientHolding(order.getClientId(), order.getInstrumentId(), afterQuantity);

        // The ledger is in the account currency, so cash is always the sum of its entries
        TransactionRecord ledger = new TransactionRecord();
        ledger.setAccountId(account.getAccountId());
        ledger.setInstrumentId(order.getInstrumentId());
        ledger.setFillId(fill.getFillId());
        ledger.setTransactionType(order.getOrderSide());
        ledger.setQuantity(quantity);
        ledger.setUnitPrice(settlement.divide(quantity, 4, RoundingMode.HALF_UP));
        ledger.setNetAmount(settlement);
        ledger.setTransactionDate(fill.getExecutedAt());
        transactionRepository.insert(ledger);

        audit("client_accounts", account.getAccountId(), order.getClientId(),
                Map.of("cash", account.getCashBalance()), Map.of("cash", newCash, "orderId", order.getOrderId()));
        audit("account_holdings", account.getAccountId(), order.getClientId(),
                Map.of("quantity", beforeQuantity, "instrumentId", order.getInstrumentId()),
                Map.of("quantity", afterQuantity, "instrumentId", order.getInstrumentId(), "orderId", order.getOrderId()));
        audit("fills", fill.getFillId(), order.getClientId(), null, fill);
        transition(order, "FILLED", null);
    }

    private BigDecimal averageCostAfterBuy(BigDecimal heldQuantity, BigDecimal heldAverage, BigDecimal boughtQuantity, BigDecimal cost) {
        BigDecimal total = heldQuantity.add(boughtQuantity);
        // Holdings from before average cost was tracked are valued at this purchase's price
        BigDecimal heldCost = heldAverage == null
                ? cost.divide(boughtQuantity, 8, RoundingMode.HALF_UP).multiply(heldQuantity)
                : heldAverage.multiply(heldQuantity);
        return heldCost.add(cost).divide(total, 8, RoundingMode.HALF_UP);
    }

    // Any symbol Fauxnance recognizes becomes tradable on first use; validateInstrument still enforces market
    private FinancialInstrumentRecord findOrRegisterInstrument(String symbol) {
        FinancialInstrumentRecord instrument = instrumentRepository.findByTicker(symbol);
        if (instrument != null) return instrument;
        FinancialInstrumentRecord discovered = quoteProvider.lookupInstrument(symbol);
        if (discovered == null) return null;
        instrumentRepository.insertIfAbsent(discovered);
        return instrumentRepository.findByTicker(discovered.getTickerSymbol());
    }

    private void validateInstrument(OrderRecord order, FinancialInstrumentRecord instrument) {
        if (instrument == null || !instrument.isTradable()) throw new IllegalArgumentException("Instrument is not tradable");
        if (!List.of("Equity", "Crypto", "FX").contains(instrument.getAssetClass())
                || !instrument.market().equals(order.getMarket())) {
            throw new IllegalArgumentException("Instrument does not match the supported market");
        }
    }

    private void validateBalances(OrderRecord order, ClientAccountRecord account, Pricing pricing) {
        BigDecimal price = pricing.unitPrice();
        if ("LIMIT".equals(order.getOrderType())) {
            boolean breached = "BUY".equals(order.getOrderSide())
                    ? price.compareTo(order.getLimitPrice()) > 0 : price.compareTo(order.getLimitPrice()) < 0;
            if (breached) throw new IllegalArgumentException("Current market quote breaches the requested limit price");
        }
        BigDecimal amount = pricing.settlementAmount();
        if (amount.signum() <= 0 || amount.precision() > 16) throw new IllegalArgumentException("Order value is outside supported precision");
        if ("BUY".equals(order.getOrderSide()) && account.getCashBalance().compareTo(amount) < 0)
            throw new IllegalArgumentException("Available cash cannot cover the requested order");
        if ("SELL".equals(order.getOrderSide()) && holdingQuantity(account.getAccountId(), order.getInstrumentId()).compareTo(order.getRequestedQuantity()) < 0)
            throw new IllegalArgumentException("Available holdings cannot cover the requested sell order");
        if ("SELL".equals(order.getOrderSide()) && account.getCashBalance().add(amount).setScale(4).precision() > 16)
            throw new IllegalArgumentException("Resulting cash balance exceeds supported precision");
        if ("BUY".equals(order.getOrderSide()) && holdingQuantity(account.getAccountId(), order.getInstrumentId())
                .add(order.getRequestedQuantity()).setScale(6).precision() > 18)
            throw new IllegalArgumentException("Resulting holding exceeds supported precision");
    }

    // No current price (market closed) or the provider is briefly unreachable: wait, don't reject
    private boolean isAwaitingPrice(ApiException problem) {
        return "QUOTE_STALE".equals(problem.getCode()) || problem instanceof ExternalServiceException;
    }

    private void defer(OrderRecord order, String reason) {
        Instant now = Instant.now();
        if (order.getSubmittedAt() != null && order.getSubmittedAt().plus(Duration.ofHours(deferralMaxHours)).isBefore(now)) {
            transition(order, "REJECTED", "Order expired: no current market price within " + deferralMaxHours + " hours");
            return;
        }
        boolean firstDeferral = order.getNextExecutionAt() == null;
        orderRepository.deferExecution(order.getOrderId(), now.plusSeconds(deferralRetrySeconds));
        if (firstDeferral) {
            // Recorded once, not on every retry, so the trail says when and why execution was delayed
            audit("orders", order.getOrderId(), order.getClientId(), Map.of("status", "ACCEPTED"),
                    Map.of("status", "ACCEPTED", "awaiting", "current market price", "reason", reason == null ? "" : reason));
        }
    }

    private BigDecimal holdingQuantity(long accountId, long instrumentId) {
        HoldingRecord holding = holdingRepository.findAccountHolding(accountId, instrumentId);
        return holding == null ? BigDecimal.ZERO : holding.getQuantity();
    }

    private ClientAccountRecord requireLockedAccount(long clientId) {
        ClientAccountRecord account = clientAccountRepository.lockDirectTradingAccount(clientId);
        if (account == null) throw new NotFoundException("ACCOUNT_NOT_FOUND", "No direct trading account exists for the signed-in client");
        return account;
    }

    private void transition(OrderRecord order, String status, String reason) {
        String previous = order.getOrderStatus();
        order.setOrderStatus(status);
        order.setRejectionReason(reason);
        orderRepository.updateStatus(order);
        audit("orders", order.getOrderId(), order.getClientId(), Map.of("status", previous),
                Map.of("status", status, "reason", reason == null ? "" : reason));

        // Publish events for downstream consumers (workers, analytics, notifications) once the change
        // has committed: consumers never see an uncommitted status, and a slow or absent broker never
        // holds the account lock (9.1/9.2). The order cache only holds final orders, so nothing to invalidate.
        if ("ACCEPTED".equals(status)) {
            afterCommit(() -> publishOrderAcceptedEvent(order));
        } else if ("FILLED".equals(status)) {
            afterCommit(() -> publishOrderFilledEvent(order));
        } else if ("REJECTED".equals(status)) {
            afterCommit(() -> publishOrderRejectedEvent(order, reason));
        }
    }

    private static void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    /**
     * Publish OrderAcceptedEvent to trigger execution workers.
     * Called when order transitions from SUBMITTED to ACCEPTED.
     */
    private void publishOrderAcceptedEvent(OrderRecord order) {
        try {
            FinancialInstrumentRecord instrument = instrumentRepository.findByTicker(order.getSymbol());
            if (instrument != null && order.getClientId() > 0) {
                OrderAcceptedEvent event = new OrderAcceptedEvent(
                        order.getOrderId(),
                        order.getClientId(),
                        order.getAccountId(),
                        order.getInstrumentId(),
                        order.getSymbol(),
                        order.getMarket(),
                        order.getOrderSide(),
                        order.getOrderType(),
                        order.getRequestedQuantity(),
                        order.getLimitPrice(),
                        Instant.now()
                );
                if (eventProducer.getIfAvailable() != null) {
                    eventProducer.getIfAvailable().publishOrderAccepted(event);
                }
            }
        } catch (Exception e) {
            // Log but don't fail - order is already ACCEPTED in database
            // Retry will happen on next polling cycle if Kafka publish failed
            org.slf4j.LoggerFactory.getLogger(this.getClass())
                    .warn("Failed to publish OrderAcceptedEvent for orderId: {}", order.getOrderId(), e);
        }
    }

    /**
     * Publish OrderFilledEvent for analytics and notifications.
     * Called when order transitions from ACCEPTED to FILLED.
     */
    private void publishOrderFilledEvent(OrderRecord order) {
        try {
            FillRecord fill = fillRepository.findByOrderId(order.getOrderId()).stream().findFirst().orElse(null);
            TransactionRecord transaction = transactionRepository.findByOrderId(order.getOrderId()).stream().findFirst().orElse(null);

            if (fill != null && transaction != null) {
                OrderFilledEvent event = new OrderFilledEvent(
                        order.getOrderId(),
                        order.getClientId(),
                        fill.getFillId(),
                        order.getSymbol(),
                        order.getOrderSide(),
                        fill.getExecutedQuantity(),
                        fill.getExecutedPrice(),
                        fill.getExecutedAt(),
                        transaction.getTransactionId()
                );
                if (eventProducer.getIfAvailable() != null) {
                    eventProducer.getIfAvailable().publishOrderFilled(event);
                }
            }
        } catch (Exception e) {
            // Log but don't fail - order is already FILLED in database
            org.slf4j.LoggerFactory.getLogger(this.getClass())
                    .warn("Failed to publish OrderFilledEvent for orderId: {}", order.getOrderId(), e);
        }
    }

    /**
     * Publish OrderRejectedEvent for notifications.
     * Called when order transitions to REJECTED.
     */
    private void publishOrderRejectedEvent(OrderRecord order, String reason) {
        try {
            String stage = "SUBMITTED".equals(order.getOrderStatus()) ? "ACCEPTANCE" : "EXECUTION";
            OrderRejectedEvent event = new OrderRejectedEvent(
                    order.getOrderId(),
                    order.getClientId(),
                    order.getSymbol(),
                    reason != null ? reason : "Unknown reason",
                    stage,
                    Instant.now()
            );
                if (eventProducer.getIfAvailable() != null) {
                    eventProducer.getIfAvailable().publishOrderRejected(event);
                }
        } catch (Exception e) {
            // Log but don't fail - order is already REJECTED in database
            org.slf4j.LoggerFactory.getLogger(this.getClass())
                    .warn("Failed to publish OrderRejectedEvent for orderId: {}", order.getOrderId(), e);
        }
    }

    private void recordPricing(OrderRecord order, Pricing pricing, String stage) {
        audit("orders", order.getOrderId(), order.getClientId(), null, pricing.auditDetail(stage));
    }

    private void audit(String entity, long id, long clientId, Object before, Object after) {
        try {
            AuditLogRecord event = new AuditLogRecord();
            event.setEntityName(entity);
            event.setEntityId(id);
            event.setClientId(clientId);
            event.setActionType(before == null ? "INSERT" : "UPDATE");
            event.setStateBefore(before == null ? null : objectMapper.writeValueAsString(before));
            event.setStateAfter(objectMapper.writeValueAsString(after));
            event.setRecordedAt(Instant.now());
            auditLogRepository.insert(event);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize trading audit", exception);
        }
    }
}
