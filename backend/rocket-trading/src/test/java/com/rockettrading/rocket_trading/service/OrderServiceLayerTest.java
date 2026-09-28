package com.rockettrading.rocket_trading.service;

import com.rockettrading.rocket_trading.dto.order.SubmitOrderRequest;
import com.rockettrading.rocket_trading.exception.ConflictException;
import com.rockettrading.rocket_trading.repository.AuditLogRepository;
import com.rockettrading.rocket_trading.repository.ClientAccountRepository;
import com.rockettrading.rocket_trading.repository.FillRepository;
import com.rockettrading.rocket_trading.repository.HoldingRepository;
import com.rockettrading.rocket_trading.repository.InstrumentRepository;
import com.rockettrading.rocket_trading.repository.MarketQuoteRepository;
import com.rockettrading.rocket_trading.repository.OrderRepository;
import com.rockettrading.rocket_trading.repository.TransactionRepository;
import com.rockettrading.rocket_trading.repository.model.ClientAccountRecord;
import com.rockettrading.rocket_trading.repository.model.FinancialInstrumentRecord;
import com.rockettrading.rocket_trading.repository.model.HoldingRecord;
import com.rockettrading.rocket_trading.repository.model.OrderRecord;
import com.rockettrading.rocket_trading.model.Quote;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderServiceLayerTest {

    @Test
    void buyOrderFillsWhenCashIsSufficient() {
        ClientAccountRepository accountRepository = mock(ClientAccountRepository.class);
        InstrumentRepository instrumentRepository = mock(InstrumentRepository.class);
        HoldingRepository holdingRepository = mock(HoldingRepository.class);
        MarketQuoteRepository marketQuoteRepository = mock(MarketQuoteRepository.class);
        OrderRepository orderRepository = mock(OrderRepository.class);
        FillRepository fillRepository = mock(FillRepository.class);
        TransactionRepository transactionRepository = mock(TransactionRepository.class);
        AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
        QuoteProvider quoteProvider = mock(QuoteProvider.class);
        OrderService orderService = new OrderService(accountRepository, instrumentRepository, holdingRepository,
                marketQuoteRepository, orderRepository, fillRepository, transactionRepository, auditLogRepository, quoteProvider);

        ClientAccountRecord account = new ClientAccountRecord();
        account.setAccountId(100L);
        account.setClientId(7L);
        account.setAccountType("DIRECT_TRADING");
        account.setCashBalance(new BigDecimal("10000.0000"));
        account.setCurrency("USD");
        account.setOpenedDate(LocalDate.now());

        FinancialInstrumentRecord instrument = new FinancialInstrumentRecord();
        instrument.setInstrumentId(55L);
        instrument.setTickerSymbol("AAPL");
        instrument.setInstrumentName("Apple");
        instrument.setAssetClass("Equity");
        instrument.setBaseCurrency("USD");
        instrument.setTradable(true);

        when(accountRepository.findDirectTradingAccountByClientId(7L)).thenReturn(account);
        when(instrumentRepository.findByTicker("AAPL")).thenReturn(instrument);
        when(quoteProvider.fetchQuote("AAPL", "stock"))
                .thenReturn(new Quote("AAPL", new BigDecimal("100.00"), new BigDecimal("101.00"),
                        new BigDecimal("100.50"), 1000, 1000, Instant.now()));
        when(orderRepository.findByIdAndClientId(1L, 7L)).thenAnswer(invocation -> {
            OrderRecord result = new OrderRecord();
            result.setOrderId(1L);
            result.setSymbol("AAPL");
            result.setOrderSide("BUY");
            result.setOrderType("MARKET");
            result.setRequestedQuantity(new BigDecimal("5.000000"));
            result.setOrderStatus("FILLED");
            result.setSubmittedAt(Instant.now());
            return result;
        });
        doAnswer(invocation -> {
            OrderRecord orderRecord = invocation.getArgument(0);
            orderRecord.setOrderId(1L);
            return 1;
        }).when(orderRepository).insert(any(OrderRecord.class));
        doAnswer(invocation -> {
            var fillRecord = invocation.getArgument(0, com.rockettrading.rocket_trading.repository.model.FillRecord.class);
            fillRecord.setFillId(10L);
            return 1;
        }).when(fillRepository).insert(any());

        var response = orderService.submitOrder(7L,
                new SubmitOrderRequest("AAPL", "BUY", new BigDecimal("5"), "stock", "MARKET", null));

        assertEquals("FILLED", response.status());
        verify(accountRepository).updateCashBalance(100L, new BigDecimal("9495.0000"));
        verify(holdingRepository).upsertAccountHolding(100L, 55L, new BigDecimal("5.000000"));
    }

    @Test
    void buyOrderRejectsWhenCashIsInsufficient() {
        ClientAccountRepository accountRepository = mock(ClientAccountRepository.class);
        InstrumentRepository instrumentRepository = mock(InstrumentRepository.class);
        HoldingRepository holdingRepository = mock(HoldingRepository.class);
        MarketQuoteRepository marketQuoteRepository = mock(MarketQuoteRepository.class);
        OrderRepository orderRepository = mock(OrderRepository.class);
        FillRepository fillRepository = mock(FillRepository.class);
        TransactionRepository transactionRepository = mock(TransactionRepository.class);
        AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
        QuoteProvider quoteProvider = mock(QuoteProvider.class);
        OrderService orderService = new OrderService(accountRepository, instrumentRepository, holdingRepository,
                marketQuoteRepository, orderRepository, fillRepository, transactionRepository, auditLogRepository, quoteProvider);

        ClientAccountRecord account = new ClientAccountRecord();
        account.setAccountId(100L);
        account.setClientId(7L);
        account.setCashBalance(new BigDecimal("50.0000"));

        FinancialInstrumentRecord instrument = new FinancialInstrumentRecord();
        instrument.setInstrumentId(55L);
        instrument.setTickerSymbol("AAPL");
        instrument.setTradable(true);

        when(accountRepository.findDirectTradingAccountByClientId(7L)).thenReturn(account);
        when(instrumentRepository.findByTicker("AAPL")).thenReturn(instrument);
        when(quoteProvider.fetchQuote("AAPL", "stock"))
                .thenReturn(new Quote("AAPL", new BigDecimal("100.00"), new BigDecimal("101.00"),
                        new BigDecimal("100.50"), 1000, 1000, Instant.now()));
        doAnswer(invocation -> {
            OrderRecord orderRecord = invocation.getArgument(0);
            orderRecord.setOrderId(1L);
            return 1;
        }).when(orderRepository).insert(any(OrderRecord.class));

        assertThrows(ConflictException.class, () -> orderService.submitOrder(
                7L,
                new SubmitOrderRequest("AAPL", "BUY", new BigDecimal("5"), "stock", "MARKET", null)
        ));

        verify(accountRepository, never()).updateCashBalance(anyLong(), any(BigDecimal.class));
    }

    @Test
    void sellOrderRejectsWhenHoldingsAreInsufficient() {
        ClientAccountRepository accountRepository = mock(ClientAccountRepository.class);
        InstrumentRepository instrumentRepository = mock(InstrumentRepository.class);
        HoldingRepository holdingRepository = mock(HoldingRepository.class);
        MarketQuoteRepository marketQuoteRepository = mock(MarketQuoteRepository.class);
        OrderRepository orderRepository = mock(OrderRepository.class);
        FillRepository fillRepository = mock(FillRepository.class);
        TransactionRepository transactionRepository = mock(TransactionRepository.class);
        AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
        QuoteProvider quoteProvider = mock(QuoteProvider.class);
        OrderService orderService = new OrderService(accountRepository, instrumentRepository, holdingRepository,
                marketQuoteRepository, orderRepository, fillRepository, transactionRepository, auditLogRepository, quoteProvider);

        ClientAccountRecord account = new ClientAccountRecord();
        account.setAccountId(100L);
        account.setClientId(7L);
        account.setCashBalance(new BigDecimal("1000.0000"));

        FinancialInstrumentRecord instrument = new FinancialInstrumentRecord();
        instrument.setInstrumentId(55L);
        instrument.setTickerSymbol("AAPL");
        instrument.setTradable(true);

        HoldingRecord holding = new HoldingRecord();
        holding.setQuantity(new BigDecimal("1.000000"));

        when(accountRepository.findDirectTradingAccountByClientId(7L)).thenReturn(account);
        when(instrumentRepository.findByTicker("AAPL")).thenReturn(instrument);
        when(holdingRepository.findAccountHolding(100L, 55L)).thenReturn(holding);
        when(quoteProvider.fetchQuote("AAPL", "stock"))
                .thenReturn(new Quote("AAPL", new BigDecimal("100.00"), new BigDecimal("101.00"),
                        new BigDecimal("100.50"), 1000, 1000, Instant.now()));
        doAnswer(invocation -> {
            OrderRecord orderRecord = invocation.getArgument(0);
            orderRecord.setOrderId(1L);
            return 1;
        }).when(orderRepository).insert(any(OrderRecord.class));

        assertThrows(ConflictException.class, () -> orderService.submitOrder(
                7L,
                new SubmitOrderRequest("AAPL", "SELL", new BigDecimal("5"), "stock", "MARKET", null)
        ));

        verify(fillRepository, never()).insert(any());
    }
}
