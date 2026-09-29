package com.rockettrading.rocket_trading.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rockettrading.rocket_trading.dto.order.*;
import com.rockettrading.rocket_trading.exception.*;
import com.rockettrading.rocket_trading.model.Quote;
import com.rockettrading.rocket_trading.repository.*;
import com.rockettrading.rocket_trading.repository.model.*;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
    private final QuoteService quoteService;
    private final Validator validator;
    private final ObjectMapper objectMapper;

    // Return business rejections normally so their records commit. The controller chooses HTTP 409.
    @Transactional
    public OrderResponse submitOrder(long clientId, SubmitOrderRequest request, String key) {
        var violations = validator.validate(request);
        if (!violations.isEmpty()) throw new IllegalArgumentException(violations.iterator().next().getMessage());
        if (key == null || !key.matches("[A-Za-z0-9._:-]{1,100}")) {
            throw new IllegalArgumentException("Idempotency-Key is required (1-100 letters, digits, '.', '_', ':', '-')");
        }
        // One account lock serializes same-client submissions and concurrent settlement.
        ClientAccountRecord account = requireLockedAccount(clientId);
        OrderRecord existing = orderRepository.findByIdempotencyKey(clientId, key);
        if (existing != null) {
            if (!request.fingerprint().equals(existing.getRequestFingerprint())) {
                throw new ConflictException("IDEMPOTENCY_CONFLICT", "Key was already used for different order details");
            }
            return OrderResponse.from(existing);
        }
        FinancialInstrumentRecord instrument = instrumentRepository.findByTicker(request.symbol());
        if (instrument == null) throw new NotFoundException("INSTRUMENT_NOT_FOUND", "Instrument is not supported");

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
            validateInstrument(order, account, instrument);
            Quote quote = quoteService.fetchCurrentQuote(order.getSymbol(), order.getMarket());
            recordPricing(order, quote, "acceptance");
            validateBalances(order, account, executionPrice(order, quote));
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
        Quote quote;
        BigDecimal price;
        try {
            validateInstrument(order, account, instrument);
            quote = quoteService.fetchCurrentQuote(order.getSymbol(), order.getMarket());
            recordPricing(order, quote, "execution");
            price = executionPrice(order, quote);
            validateBalances(order, account, price);
        } catch (ApiException | IllegalArgumentException rejection) {
            transition(order, "REJECTED", rejection.getMessage());
            return;
        }
        // Database failures escape and roll back this entire transaction. ACCEPTED survives for retry.
        MarketQuoteRecord snapshot = new MarketQuoteRecord();
        snapshot.setInstrumentId(order.getInstrumentId());
        snapshot.setBidPrice(quote.getBid());
        snapshot.setAskPrice(quote.getAsk());
        snapshot.setQuoteTimestamp(quote.getCapturedAt());
        marketQuoteRepository.insert(snapshot);
        applyFill(order, account, price, snapshot.getQuoteId());
    }

    public List<OrderResponse> listOrders(long clientId) {
        return orderRepository.findByClientId(clientId).stream().map(OrderResponse::from).toList();
    }

    public OrderResponse getOrder(long clientId, long orderId) {
        OrderRecord order = orderRepository.findByIdAndClientId(orderId, clientId);
        if (order == null) throw new NotFoundException("ORDER_NOT_FOUND", "Order was not found for the signed-in client");
        return OrderResponse.from(order);
    }

    public List<FillResponse> listFills(long clientId, long orderId) {
        getOrder(clientId, orderId);
        return fillRepository.findByOrderId(orderId).stream().map(FillResponse::from).toList();
    }

    public OrderTimelineResponse getOrderTimeline(long clientId, long orderId) {
        getOrder(clientId, orderId);
        return new OrderTimelineResponse(
                orderId,
                auditLogRepository.findTimelineForOrder(clientId, orderId).stream()
                        .map(record -> OrderTimelineEventResponse.from(record, objectMapper))
                        .toList()
        );
    }

    private void applyFill(OrderRecord order, ClientAccountRecord account, BigDecimal price, long quoteId) {
        BigDecimal notional = notional(order, price);
        FillRecord fill = new FillRecord();
        fill.setOrderId(order.getOrderId());
        fill.setExecutedQuantity(order.getRequestedQuantity());
        fill.setExecutedPrice(price);
        fill.setQuoteId(quoteId);
        fill.setExecutedAt(Instant.now());
        fillRepository.insert(fill);

        BigDecimal newCash = "BUY".equals(order.getOrderSide())
                ? account.getCashBalance().subtract(notional) : account.getCashBalance().add(notional);
        clientAccountRepository.updateCashBalance(account.getAccountId(), newCash);
        BigDecimal beforeQuantity = holdingQuantity(account.getAccountId(), order.getInstrumentId());
        BigDecimal afterQuantity = "BUY".equals(order.getOrderSide())
                ? beforeQuantity.add(order.getRequestedQuantity()) : beforeQuantity.subtract(order.getRequestedQuantity());
        holdingRepository.upsertAccountHolding(account.getAccountId(), order.getInstrumentId(), afterQuantity);
        // This phase has exactly one DIRECT_TRADING account per client; retain both existing read models.
        holdingRepository.upsertClientHolding(order.getClientId(), order.getInstrumentId(), afterQuantity);

        TransactionRecord ledger = new TransactionRecord();
        ledger.setAccountId(account.getAccountId());
        ledger.setInstrumentId(order.getInstrumentId());
        ledger.setFillId(fill.getFillId());
        ledger.setTransactionType(order.getOrderSide());
        ledger.setQuantity(order.getRequestedQuantity());
        ledger.setUnitPrice(price);
        ledger.setNetAmount(notional);
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

    private void validateInstrument(OrderRecord order, ClientAccountRecord account, FinancialInstrumentRecord instrument) {
        if (instrument == null || !instrument.isTradable()) throw new IllegalArgumentException("Instrument is not tradable");
        String expectedMarket = "Crypto".equals(instrument.getAssetClass()) ? "crypto" : "stock";
        if (!("Equity".equals(instrument.getAssetClass()) || "Crypto".equals(instrument.getAssetClass()))
                || !expectedMarket.equals(order.getMarket())) throw new IllegalArgumentException("Instrument does not match the supported market");
        if (!account.getCurrency().equals(instrument.getBaseCurrency())) throw new IllegalArgumentException("Currency conversion is not supported");
    }

    private void validateBalances(OrderRecord order, ClientAccountRecord account, BigDecimal price) {
        if ("LIMIT".equals(order.getOrderType())) {
            boolean breached = "BUY".equals(order.getOrderSide())
                    ? price.compareTo(order.getLimitPrice()) > 0 : price.compareTo(order.getLimitPrice()) < 0;
            if (breached) throw new IllegalArgumentException("Current market quote breaches the requested limit price");
        }
        BigDecimal amount = notional(order, price);
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

    private BigDecimal holdingQuantity(long accountId, long instrumentId) {
        HoldingRecord holding = holdingRepository.findAccountHolding(accountId, instrumentId);
        return holding == null ? BigDecimal.ZERO : holding.getQuantity();
    }

    private BigDecimal executionPrice(OrderRecord order, Quote quote) {
        BigDecimal price = ("BUY".equals(order.getOrderSide()) ? quote.getAsk() : quote.getBid()).setScale(4, RoundingMode.HALF_UP);
        if (price.signum() <= 0 || price.precision() > 16) throw new IllegalArgumentException("Quote price is outside supported precision");
        return price;
    }

    private BigDecimal notional(OrderRecord order, BigDecimal price) {
        return price.multiply(order.getRequestedQuantity()).setScale(4, RoundingMode.HALF_UP);
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
    }

    private void recordPricing(OrderRecord order, Quote quote, String stage) {
        audit("orders", order.getOrderId(), order.getClientId(), null,
                Map.of("stage", stage, "symbol", quote.getSymbol(), "bid", quote.getBid(), "ask", quote.getAsk(), "capturedAt", quote.getCapturedAt()));
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
