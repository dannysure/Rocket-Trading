package com.rockettrading.rocket_trading.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rockettrading.rocket_trading.dto.order.SubmitOrderRequest;
import com.rockettrading.rocket_trading.model.Quote;
import com.rockettrading.rocket_trading.service.OrderService;
import com.rockettrading.rocket_trading.service.QuoteProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Testcontainers
@SpringBootTest(properties = "trading.worker.enabled=false")
@AutoConfigureMockMvc
class TradingIntegrationIT {
    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate db;
    @Autowired OrderService service;
    @MockitoBean QuoteProvider quotes;
    String token;
    long clientId;

    @BeforeEach void setUp() throws Exception {
        // Only this disposable container is reset; never use a developer database in tests.
        db.execute("TRUNCATE audit_logs, transactions, fills, orders, market_quotes, account_holdings, client_holdings, client_sessions, client_accounts, client_profiles RESTART IDENTITY CASCADE");
        db.update("UPDATE financial_instruments SET is_tradable = true");
        doAnswer(i -> quote(i.getArgument(0), "100", "101", Instant.now())).when(quotes).fetchQuote(anyString(), anyString());
        JsonNode session = registerAndSignIn("10000");
        token = session.path("accessToken").asText();
        clientId = session.path("clientId").asLong();
    }

    @Test void acceptsBeforeExecutionThenBuySellUpdatePortfolioHistoryAndAudit() throws Exception {
        long buyId = accepted("BUY", "5", "buy-1");
        assertEquals("ACCEPTED", orderStatus(buyId));
        assertEquals(0, count("fills"));
        assertMoney("10000", cash());
        service.executeOrder(buyId);
        service.executeOrder(buyId);
        assertEquals("FILLED", orderStatus(buyId));
        assertEquals(1, count("fills"));
        assertEquals(1, count("transactions"));
        assertMoney("9495", cash());
        mvc.perform(get("/api/v1/portfolio/summary").header("Authorization", bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.positions[0].quantity").value(5));
        mvc.perform(get("/api/v1/fills/" + buyId).header("Authorization", bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].executedPrice").value(101));
        long sellId = accepted("SELL", "2", "sell-1");
        service.executeOrder(sellId);
        assertMoney("9695", cash());
        assertMoney("3", db.queryForObject("SELECT quantity FROM account_holdings", BigDecimal.class));
        assertMoney("3", db.queryForObject("SELECT total_quantity FROM client_holdings", BigDecimal.class));
        mvc.perform(get("/api/v1/orders").header("Authorization", bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].orderId").value(sellId));
        assertTrue(db.queryForObject("SELECT count(*) FROM audit_logs WHERE state_after LIKE '%execution%'", Integer.class) >= 2);
        assertTrue(db.queryForObject("SELECT count(*) FROM audit_logs WHERE entity_name = 'client_accounts'", Integer.class) >= 2);
        assertThrows(RuntimeException.class, () -> db.update("UPDATE fills SET executed_price=1"));
        assertThrows(RuntimeException.class, () -> db.update("DELETE FROM audit_logs"));
    }

    @Test void rejectedOrdersCommitWithoutChangingFinancialRecords() throws Exception {
        submit("BUY", "1000", "too-large").andExpect(status().isConflict());
        submit("SELL", "1", "no-holdings").andExpect(status().isConflict());
        db.update("UPDATE financial_instruments SET is_tradable=false WHERE ticker_symbol='AAPL'");
        submit("BUY", "1", "not-tradable").andExpect(status().isConflict());
        assertEquals(3, count("orders"));
        assertEquals(3, db.queryForObject("SELECT count(*) FROM orders WHERE order_status='REJECTED' AND rejection_reason IS NOT NULL", Integer.class));
        assertEquals(0, count("fills"));
        assertMoney("10000", cash());
    }

    @Test void executionUsesNewQuoteAndRejectsStaleOrUnavailablePrices() throws Exception {
        long filled = accepted("BUY", "1", "new-price");
        doAnswer(i -> quote("AAPL", "109", "110", Instant.now())).when(quotes).fetchQuote(anyString(), anyString());
        service.executeOrder(filled);
        assertMoney("110", db.queryForObject("SELECT executed_price FROM fills", BigDecimal.class));
        long stale = accepted("BUY", "1", "stale");
        doReturn(quote("AAPL", "100", "101", Instant.now().minusSeconds(120))).when(quotes).fetchQuote(anyString(), anyString());
        service.executeOrder(stale);
        assertEquals("REJECTED", orderStatus(stale));
        assertMoney("9890", cash());
        doAnswer(i -> quote("AAPL", "100", "101", Instant.now())).when(quotes).fetchQuote(anyString(), anyString());
        long unavailable = accepted("BUY", "1", "unavailable");
        doThrow(new com.rockettrading.rocket_trading.exception.ExternalServiceException("PRICE_UNAVAILABLE", "Quote unavailable")).when(quotes).fetchQuote(anyString(), anyString());
        service.executeOrder(unavailable);
        assertEquals("REJECTED", orderStatus(unavailable));
        assertEquals(1, count("fills"));
    }

    @Test void limitIsRecheckedAtExecution() throws Exception {
        var response = mvc.perform(post("/api/v1/orders").header("Authorization", bearer()).header("Idempotency-Key", "limit")
                .contentType("application/json").content("{\"symbol\":\"AAPL\",\"side\":\"BUY\",\"quantity\":1,\"orderType\":\"LIMIT\",\"limitPrice\":102}"))
                .andExpect(status().isAccepted()).andReturn();
        long id = mapper.readTree(response.getResponse().getContentAsString()).path("data").path("orderId").asLong();
        doAnswer(i -> quote("AAPL", "103", "104", Instant.now())).when(quotes).fetchQuote(anyString(), anyString());
        service.executeOrder(id);
        assertEquals("REJECTED", orderStatus(id));
        assertMoney("10000", cash());
    }

    @Test void rollbackAfterCashAndHoldingsUpdatesPreservesAcceptedOrderForRetry() throws Exception {
        long id = accepted("BUY", "5", "rollback");
        db.execute("CREATE FUNCTION test_fail_ledger() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'Injected settlement failure'; END $$");
        db.execute("CREATE TRIGGER test_fail_ledger BEFORE INSERT ON transactions FOR EACH ROW EXECUTE FUNCTION test_fail_ledger()");
        try {
            assertThrows(RuntimeException.class, () -> service.executeOrder(id));
            assertEquals("ACCEPTED", orderStatus(id));
            assertMoney("10000", cash());
            for (String table : new String[]{"fills", "transactions", "account_holdings", "client_holdings", "market_quotes"}) assertEquals(0, count(table), table);
            assertEquals(0, db.queryForObject("SELECT count(*) FROM audit_logs WHERE state_after LIKE '%execution%'", Integer.class));
        } finally {
            db.execute("DROP TRIGGER test_fail_ledger ON transactions");
            db.execute("DROP FUNCTION test_fail_ledger()");
        }
        service.executeOrder(id);
        assertEquals("FILLED", orderStatus(id));
        assertEquals(1, count("fills"));
    }

    @Test void scopesOrdersFillsAndPortfolioToAuthenticatedClient() throws Exception {
        long id = accepted("BUY", "1", "owner");
        service.executeOrder(id);
        String other = registerAndSignIn("500").path("accessToken").asText();
        for (String route : new String[]{"orders/", "fills/"}) {
            mvc.perform(get("/api/v1/" + route + id).header("Authorization", "Bearer " + other)).andExpect(status().isNotFound());
        }
        mvc.perform(get("/api/v1/orders").header("Authorization", "Bearer " + other)).andExpect(jsonPath("$.data.length()").value(0));
        mvc.perform(get("/api/v1/portfolio/summary").header("Authorization", "Bearer " + other))
                .andExpect(jsonPath("$.data.cashBalance").value(500)).andExpect(jsonPath("$.data.positions.length()").value(0));
    }

    @Test void rejectsUnauthenticatedExpiredAndRevokedSessions() throws Exception {
        mvc.perform(get("/api/v1/orders")).andExpect(status().isUnauthorized());
        db.update("UPDATE client_sessions SET expires_at = now() - interval '1 second'");
        mvc.perform(get("/api/v1/orders").header("Authorization", bearer())).andExpect(status().isUnauthorized());
        token = registerAndSignIn("100").path("accessToken").asText();
        mvc.perform(post("/api/v1/auth/sign-out").header("Authorization", bearer())).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/orders").header("Authorization", bearer())).andExpect(status().isUnauthorized());
    }

    @Test void validatesRequestsAndRequiresIdempotencyKey() throws Exception {
        for (String body : new String[]{"{\"symbol\":\"AAPL\",\"side\":\"BUY\"}", "{\"symbol\":\"AAPL\",\"side\":\"BUY\",\"quantity\":-1}",
                "{\"symbol\":\"AAPL\",\"side\":\"BUY\",\"quantity\":1,\"orderType\":\"LIMIT\"}"}) {
            mvc.perform(post("/api/v1/orders").header("Authorization", bearer()).header("Idempotency-Key", UUID.randomUUID().toString())
                    .contentType("application/json").content(body)).andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/v1/orders").header("Authorization", bearer()).contentType("application/json").content(body("BUY", "1")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/orders").header("Authorization", bearer()).header("Idempotency-Key", "unsupported")
                .contentType("application/json").content(body("BUY", "1").replace("AAPL", "UNKNOWN"))).andExpect(status().isNotFound());
        assertEquals(0, count("orders"));
    }

    @Test void retriesReturnSameOrderAndConflictingPayloadReturns409() throws Exception {
        long id = accepted("BUY", "1", "retry");
        assertEquals(id, accepted("BUY", "1.000", "retry"));
        service.executeOrder(id);
        assertEquals(id, accepted("BUY", "1", "retry"));
        submit("BUY", "2", "retry").andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_CONFLICT"));
        assertEquals(1, count("orders"));
        assertEquals(1, count("fills"));
    }

    @Test void concurrentSubmissionsWithSameKeyCreateOneOrder() throws Exception {
        var request = new SubmitOrderRequest("AAPL", "BUY", BigDecimal.ONE, "stock", "MARKET", null);
        runTogether(() -> service.submitOrder(clientId, request, "concurrent"), () -> service.submitOrder(clientId, request, "concurrent"));
        assertEquals(1, count("orders"));
    }

    @Test void concurrentExecutionsCannotOverspendOrOversell() throws Exception {
        db.update("UPDATE client_accounts SET cash_balance=150 WHERE client_id=?", clientId);
        long first = accepted("BUY", "1", "first");
        long second = accepted("BUY", "1", "second");
        runTogether(() -> { service.executeOrder(first); return null; }, () -> { service.executeOrder(second); return null; });
        assertMoney("49", cash());
        assertEquals(1, count("fills"));
        long sell1 = accepted("SELL", "1", "sell1");
        long sell2 = accepted("SELL", "1", "sell2");
        runTogether(() -> { service.executeOrder(sell1); return null; }, () -> { service.executeOrder(sell2); return null; });
        assertMoney("149", cash());
        assertMoney("0", db.queryForObject("SELECT quantity FROM account_holdings", BigDecimal.class));
        assertEquals(2, count("fills"));
    }

    @Test void concurrentWorkersCannotFillSameOrderTwice() throws Exception {
        long id = accepted("BUY", "1", "workers");
        runTogether(() -> { service.executeOrder(id); return null; }, () -> { service.executeOrder(id); return null; });
        assertEquals(1, count("fills"));
        assertMoney("9899", cash());
    }

    private void runTogether(Callable<?> first, Callable<?> second) throws Exception {
        try (var pool = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            var a = pool.submit(() -> { start.await(); return first.call(); });
            var b = pool.submit(() -> { start.await(); return second.call(); });
            start.countDown();
            a.get(15, TimeUnit.SECONDS); b.get(15, TimeUnit.SECONDS);
        }
    }
    private JsonNode registerAndSignIn(String cash) throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        mvc.perform(post("/api/v1/auth/register").contentType("application/json").content("{\"name\":\"Test\",\"email\":\""+email+"\",\"initialCash\":"+cash+"}"))
                .andExpect(status().isCreated());
        var result = mvc.perform(post("/api/v1/auth/sign-in").contentType("application/json").content("{\"email\":\""+email+"\"}"))
                .andExpect(status().isOk()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString()).path("data");
    }
    private ResultActions submit(String side, String quantity, String key) throws Exception {
        return mvc.perform(post("/api/v1/orders").header("Authorization", bearer()).header("Idempotency-Key", key)
                .contentType("application/json").content(body(side, quantity)));
    }
    private long accepted(String side, String quantity, String key) throws Exception {
        return mapper.readTree(submit(side, quantity, key).andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString()).path("data").path("orderId").asLong();
    }
    private String body(String side, String quantity) { return "{\"symbol\":\"AAPL\",\"side\":\""+side+"\",\"quantity\":"+quantity+",\"market\":\"stock\",\"orderType\":\"MARKET\"}"; }
    private String bearer() { return "Bearer " + token; }
    private String orderStatus(long id) { return db.queryForObject("SELECT order_status FROM orders WHERE order_id=?", String.class, id); }
    private int count(String table) { return db.queryForObject("SELECT count(*) FROM " + table, Integer.class); }
    private BigDecimal cash() { return db.queryForObject("SELECT cash_balance FROM client_accounts WHERE client_id=?", BigDecimal.class, clientId); }
    private void assertMoney(String expected, BigDecimal actual) { assertEquals(0, new BigDecimal(expected).compareTo(actual)); }
    private Quote quote(String symbol, String bid, String ask, Instant at) { return new Quote(symbol, new BigDecimal(bid), new BigDecimal(ask), new BigDecimal(ask), 100, 100, at); }
}
