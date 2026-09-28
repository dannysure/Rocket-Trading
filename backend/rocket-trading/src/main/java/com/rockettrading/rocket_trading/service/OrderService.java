package com.rockettrading.rocket_trading.service;

import com.rockettrading.rocket_trading.dto.order.FillResponse;
import com.rockettrading.rocket_trading.dto.order.OrderResponse;
import com.rockettrading.rocket_trading.dto.order.SubmitOrderRequest;
import com.rockettrading.rocket_trading.exception.ConflictException;
import com.rockettrading.rocket_trading.exception.NotFoundException;
import com.rockettrading.rocket_trading.model.Quote;
import com.rockettrading.rocket_trading.repository.AuditLogRepository;
import com.rockettrading.rocket_trading.repository.ClientAccountRepository;
import com.rockettrading.rocket_trading.repository.FillRepository;
import com.rockettrading.rocket_trading.repository.HoldingRepository;
import com.rockettrading.rocket_trading.repository.InstrumentRepository;
import com.rockettrading.rocket_trading.repository.MarketQuoteRepository;
import com.rockettrading.rocket_trading.repository.OrderRepository;
import com.rockettrading.rocket_trading.repository.TransactionRepository;
import com.rockettrading.rocket_trading.repository.model.AuditLogRecord;
import com.rockettrading.rocket_trading.repository.model.ClientAccountRecord;
import com.rockettrading.rocket_trading.repository.model.FillRecord;
import com.rockettrading.rocket_trading.repository.model.FinancialInstrumentRecord;
import com.rockettrading.rocket_trading.repository.model.HoldingRecord;
import com.rockettrading.rocket_trading.repository.model.MarketQuoteRecord;
import com.rockettrading.rocket_trading.repository.model.OrderRecord;
import com.rockettrading.rocket_trading.repository.model.TransactionRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Service
public class OrderService {
    private final ClientAccountRepository clientAccountRepository;
    private final InstrumentRepository instrumentRepository;
    private final HoldingRepository holdingRepository;
    private final MarketQuoteRepository marketQuoteRepository;
    private final OrderRepository orderRepository;
    private final FillRepository fillRepository;
    private final TransactionRepository transactionRepository;
    private final AuditLogRepository auditLogRepository;
    private final QuoteProvider quoteProvider;

    public OrderService(ClientAccountRepository clientAccountRepository,
                        InstrumentRepository instrumentRepository,
                        HoldingRepository holdingRepository,
                        MarketQuoteRepository marketQuoteRepository,
                        OrderRepository orderRepository,
                        FillRepository fillRepository,
                        TransactionRepository transactionRepository,
                        AuditLogRepository auditLogRepository,
                        QuoteProvider quoteProvider) {
        this.clientAccountRepository = clientAccountRepository;
        this.instrumentRepository = instrumentRepository;
        this.holdingRepository = holdingRepository;
        this.marketQuoteRepository = marketQuoteRepository;
        this.orderRepository = orderRepository;
        this.fillRepository = fillRepository;
        this.transactionRepository = transactionRepository;
        this.auditLogRepository = auditLogRepository;
        this.quoteProvider = quoteProvider;
    }

    @Transactional
    public OrderResponse submitOrder(long clientId, SubmitOrderRequest request) {
        ClientAccountRecord account = requireAccount(clientId);
        FinancialInstrumentRecord instrument = resolveInstrument(request.symbol(), request.market());
        OrderRecord orderRecord = new OrderRecord();
        orderRecord.setClientId(clientId);
        orderRecord.setAccountId(account.getAccountId());
        orderRecord.setInstrumentId(instrument.getInstrumentId());
        orderRecord.setSymbol(instrument.getTickerSymbol());
        orderRecord.setOrderSide(normalizeSide(request.side()));
        orderRecord.setOrderType(normalizeOrderType(request.orderType()));
        orderRecord.setRequestedQuantity(request.quantity().setScale(6, RoundingMode.HALF_UP));
        orderRecord.setLimitPrice(request.limitPrice());
        orderRecord.setOrderStatus("SUBMITTED");
        orderRepository.insert(orderRecord);
        audit("orders", orderRecord.getOrderId(), "INSERT", clientId, null, "SUBMITTED");

        if (!instrument.isTradable()) {
            rejectOrder(orderRecord, clientId, "Instrument is not tradable");
        }

        Quote quote = quoteProvider.fetchQuote(instrument.getTickerSymbol(), resolveMarket(request.market()));
        BigDecimal executionPrice = resolveExecutionPrice(orderRecord.getOrderSide(), quote);
        enforceLimitPrice(orderRecord, executionPrice, clientId);
        validateBalances(orderRecord, account, executionPrice, clientId);

        orderRecord.setOrderStatus("ACCEPTED");
        orderRepository.updateStatus(orderRecord);
        audit("orders", orderRecord.getOrderId(), "UPDATE", clientId, "SUBMITTED", "ACCEPTED");

        MarketQuoteRecord marketQuoteRecord = new MarketQuoteRecord();
        marketQuoteRecord.setInstrumentId(instrument.getInstrumentId());
        marketQuoteRecord.setBidPrice(quote.getBid());
        marketQuoteRecord.setAskPrice(quote.getAsk());
        marketQuoteRecord.setQuoteTimestamp(quote.getCapturedAt());
        marketQuoteRepository.insert(marketQuoteRecord);

        applyFill(orderRecord, account, instrument, executionPrice, marketQuoteRecord.getQuoteId(), clientId);

        OrderRecord persisted = orderRepository.findByIdAndClientId(orderRecord.getOrderId(), clientId);
        return OrderResponse.from(persisted);
    }

    public List<OrderResponse> listOrders(long clientId) {
        return orderRepository.findByClientId(clientId).stream().map(OrderResponse::from).toList();
    }

    public OrderResponse getOrder(long clientId, long orderId) {
        OrderRecord orderRecord = orderRepository.findByIdAndClientId(orderId, clientId);
        if (orderRecord == null) {
            throw new NotFoundException("ORDER_NOT_FOUND", "Order was not found for the signed-in client");
        }
        return OrderResponse.from(orderRecord);
    }

    public List<FillResponse> listFills(long orderId) {
        return fillRepository.findByOrderId(orderId).stream().map(FillResponse::from).toList();
    }

    private void applyFill(OrderRecord orderRecord,
                           ClientAccountRecord account,
                           FinancialInstrumentRecord instrument,
                           BigDecimal executionPrice,
                           Long quoteId,
                           long clientId) {
        BigDecimal notional = executionPrice.multiply(orderRecord.getRequestedQuantity()).setScale(4, RoundingMode.HALF_UP);

        FillRecord fillRecord = new FillRecord();
        fillRecord.setOrderId(orderRecord.getOrderId());
        fillRecord.setExecutedQuantity(orderRecord.getRequestedQuantity());
        fillRecord.setExecutedPrice(executionPrice);
        fillRecord.setQuoteId(quoteId);
        fillRecord.setExecutedAt(Instant.now());
        fillRepository.insert(fillRecord);

        BigDecimal newCashBalance = updateCash(orderRecord.getOrderSide(), account.getCashBalance(), notional);
        clientAccountRepository.updateCashBalance(account.getAccountId(), newCashBalance);

        updateHoldings(orderRecord, instrument, clientId);

        TransactionRecord transactionRecord = new TransactionRecord();
        transactionRecord.setAccountId(account.getAccountId());
        transactionRecord.setInstrumentId(instrument.getInstrumentId());
        transactionRecord.setFillId(fillRecord.getFillId());
        transactionRecord.setTransactionType(orderRecord.getOrderSide());
        transactionRecord.setQuantity(orderRecord.getRequestedQuantity());
        transactionRecord.setUnitPrice(executionPrice);
        transactionRecord.setNetAmount(notional);
        transactionRecord.setTransactionDate(Instant.now());
        transactionRepository.insert(transactionRecord);

        orderRecord.setOrderStatus("FILLED");
        orderRepository.updateStatus(orderRecord);
        audit("fills", fillRecord.getFillId(), "INSERT", clientId, null, "FILLED");
        audit("orders", orderRecord.getOrderId(), "UPDATE", clientId, "ACCEPTED", "FILLED");
    }

    private void updateHoldings(OrderRecord orderRecord, FinancialInstrumentRecord instrument, long clientId) {
        HoldingRecord accountHolding = holdingRepository.findAccountHolding(orderRecord.getAccountId(), instrument.getInstrumentId());
        HoldingRecord clientHolding = holdingRepository.findClientHolding(clientId, instrument.getInstrumentId());
        BigDecimal accountQuantity = accountHolding == null ? BigDecimal.ZERO : accountHolding.getQuantity();
        BigDecimal clientQuantity = clientHolding == null ? BigDecimal.ZERO : clientHolding.getQuantity();

        BigDecimal updatedAccountQuantity;
        BigDecimal updatedClientQuantity;
        if ("BUY".equals(orderRecord.getOrderSide())) {
            updatedAccountQuantity = accountQuantity.add(orderRecord.getRequestedQuantity());
            updatedClientQuantity = clientQuantity.add(orderRecord.getRequestedQuantity());
        } else {
            updatedAccountQuantity = accountQuantity.subtract(orderRecord.getRequestedQuantity());
            updatedClientQuantity = clientQuantity.subtract(orderRecord.getRequestedQuantity());
        }

        holdingRepository.upsertAccountHolding(orderRecord.getAccountId(), instrument.getInstrumentId(), updatedAccountQuantity.max(BigDecimal.ZERO));
        holdingRepository.upsertClientHolding(clientId, instrument.getInstrumentId(), updatedClientQuantity.max(BigDecimal.ZERO));
    }

    private BigDecimal updateCash(String side, BigDecimal currentCashBalance, BigDecimal notional) {
        if ("BUY".equals(side)) {
            return currentCashBalance.subtract(notional);
        }
        return currentCashBalance.add(notional);
    }

    private void validateBalances(OrderRecord orderRecord, ClientAccountRecord account, BigDecimal executionPrice, long clientId) {
        BigDecimal notional = executionPrice.multiply(orderRecord.getRequestedQuantity()).setScale(4, RoundingMode.HALF_UP);
        if ("BUY".equals(orderRecord.getOrderSide()) && account.getCashBalance().compareTo(notional) < 0) {
            rejectOrder(orderRecord, clientId, "Available cash cannot cover the requested order");
        }

        if ("SELL".equals(orderRecord.getOrderSide())) {
            HoldingRecord holdingRecord = holdingRepository.findAccountHolding(orderRecord.getAccountId(), orderRecord.getInstrumentId());
            BigDecimal quantity = holdingRecord == null ? BigDecimal.ZERO : holdingRecord.getQuantity();
            if (quantity.compareTo(orderRecord.getRequestedQuantity()) < 0) {
                rejectOrder(orderRecord, clientId, "Available holdings cannot cover the requested sell order");
            }
        }
    }

    private void enforceLimitPrice(OrderRecord orderRecord, BigDecimal executionPrice, long clientId) {
        if (!"LIMIT".equals(orderRecord.getOrderType()) || orderRecord.getLimitPrice() == null) {
            return;
        }

        boolean limitBreached = "BUY".equals(orderRecord.getOrderSide())
                ? executionPrice.compareTo(orderRecord.getLimitPrice()) > 0
                : executionPrice.compareTo(orderRecord.getLimitPrice()) < 0;
        if (limitBreached) {
            rejectOrder(orderRecord, clientId, "Current market quote breaches the requested limit price");
        }
    }

    private BigDecimal resolveExecutionPrice(String side, Quote quote) {
        return "BUY".equals(side) ? quote.getAsk() : quote.getBid();
    }

    private String normalizeSide(String side) {
        String normalized = side == null ? "" : side.trim().toUpperCase(Locale.ROOT);
        if (!"BUY".equals(normalized) && !"SELL".equals(normalized)) {
            throw new IllegalArgumentException("side must be BUY or SELL");
        }
        return normalized;
    }

    private String normalizeOrderType(String orderType) {
        if (orderType == null || orderType.isBlank()) {
            return "MARKET";
        }
        String normalized = orderType.trim().toUpperCase(Locale.ROOT);
        if (!"MARKET".equals(normalized) && !"LIMIT".equals(normalized)) {
            throw new IllegalArgumentException("orderType must be MARKET or LIMIT");
        }
        return normalized;
    }

    private ClientAccountRecord requireAccount(long clientId) {
        ClientAccountRecord account = clientAccountRepository.findDirectTradingAccountByClientId(clientId);
        if (account == null) {
            throw new NotFoundException("ACCOUNT_NOT_FOUND", "No direct trading account exists for the signed-in client");
        }
        return account;
    }

    private FinancialInstrumentRecord resolveInstrument(String symbol, String market) {
        FinancialInstrumentRecord instrument = instrumentRepository.findByTicker(symbol.toUpperCase(Locale.ROOT));
        if (instrument != null) {
            return instrument;
        }

        FinancialInstrumentRecord created = new FinancialInstrumentRecord();
        created.setTickerSymbol(symbol.trim().toUpperCase(Locale.ROOT));
        created.setInstrumentName(created.getTickerSymbol());
        created.setAssetClass("crypto".equalsIgnoreCase(resolveMarket(market)) ? "Crypto" : "Equity");
        created.setBaseCurrency("USD");
        created.setTradable(true);
        instrumentRepository.insert(created);
        return created;
    }

    private String resolveMarket(String market) {
        if (market == null || market.isBlank()) {
            return "stock";
        }
        String normalized = market.trim().toLowerCase(Locale.ROOT);
        if (!"stock".equals(normalized) && !"crypto".equals(normalized)) {
            throw new IllegalArgumentException("market must be stock or crypto");
        }
        return normalized;
    }

    private void rejectOrder(OrderRecord orderRecord, long clientId, String reason) {
        orderRecord.setOrderStatus("REJECTED");
        orderRecord.setRejectionReason(reason);
        orderRepository.updateStatus(orderRecord);
        audit("orders", orderRecord.getOrderId(), "UPDATE", clientId, "SUBMITTED", "REJECTED");
        throw new ConflictException("ORDER_REJECTED", reason);
    }

    private void audit(String entityName, long entityId, String actionType, long clientId, String stateBefore, String stateAfter) {
        AuditLogRecord auditLogRecord = new AuditLogRecord();
        auditLogRecord.setEntityName(entityName);
        auditLogRecord.setEntityId(entityId);
        auditLogRecord.setActionType(actionType);
        auditLogRecord.setClientId(clientId);
        auditLogRecord.setStateBefore(stateBefore);
        auditLogRecord.setStateAfter(stateAfter);
        auditLogRecord.setRecordedAt(Instant.now());
        auditLogRepository.insert(auditLogRecord);
    }
}
