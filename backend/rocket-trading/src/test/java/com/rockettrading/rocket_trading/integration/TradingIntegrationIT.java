package com.rockettrading.rocket_trading.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rockettrading.rocket_trading.dto.order.SubmitOrderRequest;
import com.rockettrading.rocket_trading.model.Quote;
import com.rockettrading.rocket_trading.service.OrderService;
import com.rockettrading.rocket_trading.service.QuoteProvider;
import com.rockettrading.rocket_trading.service.ReportingSnapshot;
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
// Worker and scheduled reporting refresh stay out of the way; tests drive both explicitly
// Quote freshness is pinned because Spring also imports the developer's .env, which may widen it
@SpringBootTest(properties = {"trading.worker.enabled=false", "reporting.refresh-ms=3600000", "fixtures.password=", "catalog.sync.enabled=false",
        "quotes.fauxnance.max-age-seconds=900", "quotes.fauxnance.fx-max-age-seconds=345600"})
@AutoConfigureMockMvc
class TradingIntegrationIT {
    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate db;
    @Autowired OrderService service;
    @Autowired ReportingSnapshot reporting;
    @MockitoBean QuoteProvider quotes;
    String token;
    long clientId;

    @BeforeEach void setUp() throws Exception {
        // Only this disposable container is reset; never use a developer database in tests.
        db.execute("TRUNCATE staff_access_log, client_watchlist, audit_logs, transactions, fills, orders, market_quotes, account_holdings, client_holdings, client_sessions, client_accounts, client_profiles RESTART IDENTITY CASCADE");
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
        assertEquals(1, tradeLedgerEntries());
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

    @Test void executionUsesNewQuoteAndWaitsForMarketWhenPriceIsStaleOrUnavailable() throws Exception {
        long filled = accepted("BUY", "1", "new-price");
        doAnswer(i -> quote("AAPL", "109", "110", Instant.now())).when(quotes).fetchQuote(anyString(), anyString());
        service.executeOrder(filled);
        assertMoney("110", db.queryForObject("SELECT executed_price FROM fills", BigDecimal.class));

        // Older than quotes.fauxnance.max-age-seconds (default 900): the market is closed, so the order waits
        long stale = accepted("BUY", "1", "stale");
        doReturn(quote("AAPL", "100", "101", Instant.now().minusSeconds(3600))).when(quotes).fetchQuote(anyString(), anyString());
        service.executeOrder(stale);
        assertEquals("ACCEPTED", orderStatus(stale));
        assertNotNull(db.queryForObject("SELECT next_execution_at FROM orders WHERE order_id=?", Instant.class, stale));
        mvc.perform(get("/api/v1/orders/" + stale).header("Authorization", bearer()))
                .andExpect(jsonPath("$.data.awaitingMarket").value(true));
        assertMoney("9890", cash());

        // The provider being briefly unreachable is also a delay, not a rejection
        doThrow(new com.rockettrading.rocket_trading.exception.ExternalServiceException("PRICE_UNAVAILABLE", "Quote unavailable")).when(quotes).fetchQuote(anyString(), anyString());
        service.executeOrder(stale);
        assertEquals("ACCEPTED", orderStatus(stale));
        assertEquals(1, db.queryForObject("SELECT count(*) FROM audit_logs WHERE state_after LIKE '%awaiting%'", Integer.class));

        // Once a current price exists the waiting order fills at it
        doAnswer(i -> quote("AAPL", "104", "105", Instant.now())).when(quotes).fetchQuote(anyString(), anyString());
        service.executeOrder(stale);
        assertEquals("FILLED", orderStatus(stale));
        assertMoney("9785", cash());
        assertEquals(2, count("fills"));
    }

    @Test void ordersWaitingTooLongForAPriceExpireWithoutTouchingCash() throws Exception {
        long id = accepted("BUY", "1", "expires");
        db.update("UPDATE orders SET submitted_at = now() - interval '97 hours' WHERE order_id=?", id);
        doReturn(quote("AAPL", "100", "101", Instant.now().minusSeconds(3600))).when(quotes).fetchQuote(anyString(), anyString());
        service.executeOrder(id);
        assertEquals("REJECTED", orderStatus(id));
        assertTrue(db.queryForObject("SELECT rejection_reason FROM orders WHERE order_id=?", String.class, id).contains("expired"));
        assertMoney("10000", cash());
        assertEquals(0, count("fills"));
    }

    @Test void closedMarketStillAcceptsAndTellsTheClientBeforeTheyPlaceIt() throws Exception {
        doReturn(quote("AAPL", "100", "101", Instant.now().minusSeconds(3600))).when(quotes).fetchQuote(anyString(), anyString());
        mvc.perform(get("/api/v1/quotes/AAPL/indicative?side=BUY&quantity=2").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.marketOpen").value(false))
                .andExpect(jsonPath("$.data.estimatedTotal").value(202));
        long id = accepted("BUY", "2", "closed-market");
        assertEquals("ACCEPTED", orderStatus(id));
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
            for (String table : new String[]{"fills", "account_holdings", "client_holdings", "market_quotes"}) assertEquals(0, count(table), table);
            assertEquals(0, tradeLedgerEntries());
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

    @Test void exposesProfileInstrumentCatalogAndOrderTimeline() throws Exception {
        long id = accepted("BUY", "1", "timeline");
        service.executeOrder(id);

        mvc.perform(get("/api/v1/me").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.clientId").value(clientId))
                .andExpect(jsonPath("$.data.email").exists());

        mvc.perform(get("/api/v1/instruments").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(22))
                .andExpect(jsonPath("$.data[0].symbol").exists());

        mvc.perform(get("/api/v1/orders/" + id + "/timeline").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderId").value(id))
                .andExpect(jsonPath("$.data.events.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(5)));
    }

    @Test void reportingIsForInternalRolesAndReadsTheSnapshotNotLiveTables() throws Exception {
        long buyId = accepted("BUY", "2", "report-buy");
        service.executeOrder(buyId);
        long sellId = accepted("SELL", "1", "report-sell");
        service.executeOrder(sellId);
        String analyst = staffToken("ANALYST");

        mvc.perform(get("/api/v1/reporting/overview").header("Authorization", bearer()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        reporting.refresh();
        // Placed after the snapshot: invisible to reporting until the next refresh
        accepted("BUY", "1", "after-snapshot");

        mvc.perform(get("/api/v1/reporting/overview").header("Authorization", "Bearer " + analyst))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalOrders").value(2))
                .andExpect(jsonPath("$.data.filledOrders").value(2))
                .andExpect(jsonPath("$.data.totalFills").value(2))
                .andExpect(jsonPath("$.data.totalNotional").value(302))
                .andExpect(jsonPath("$.data.activeClients").value(1))
                .andExpect(jsonPath("$.data.dataAsOf").exists());

        mvc.perform(get("/api/v1/reporting/instruments").header("Authorization", "Bearer " + analyst))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].symbol").value("AAPL"))
                .andExpect(jsonPath("$.data[0].segment").value("US"))
                .andExpect(jsonPath("$.data[0].orderCount").value(2));

        mvc.perform(get("/api/v1/reporting/segments").header("Authorization", "Bearer " + analyst))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].riskProfile").value("Balanced"))
                .andExpect(jsonPath("$.data[0].orderCount").value(2));

        mvc.perform(get("/api/v1/reporting/volume?bucket=day").header("Authorization", "Bearer " + analyst))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].orderCount").value(2));
        mvc.perform(get("/api/v1/reporting/markets").header("Authorization", "Bearer " + analyst))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].segment").value("US"));

        reporting.refresh();
        mvc.perform(get("/api/v1/reporting/overview").header("Authorization", "Bearer " + analyst))
                .andExpect(jsonPath("$.data.totalOrders").value(3));
    }

    @Test void foreignInstrumentsSettleInAccountCurrencyAtTheRecordedFxRate() throws Exception {
        doAnswer(i -> switch ((String) i.getArgument(0)) {
            case "RELIANCE.NS" -> quote("RELIANCE.NS", "1000", "1001", Instant.now());
            case "FX:INRUSD" -> quote("FX:INRUSD", "0.0119", "0.0121", Instant.now().minusSeconds(18 * 3600));
            case "FX:EURUSD" -> quote("FX:EURUSD", "1.10", "1.11", Instant.now().minusSeconds(18 * 3600));
            default -> quote(i.getArgument(0), "100", "101", Instant.now());
        }).when(quotes).fetchQuote(anyString(), anyString());

        mvc.perform(get("/api/v1/quotes/RELIANCE.NS/indicative?side=BUY&quantity=2").header("Authorization", bearer()))
                .andExpect(jsonPath("$.data.unitPrice").value(1001))
                .andExpect(jsonPath("$.data.instrumentCurrency").value("INR"))
                .andExpect(jsonPath("$.data.settlementCurrency").value("USD"))
                .andExpect(jsonPath("$.data.estimatedTotal").value(24.2242))
                .andExpect(jsonPath("$.data.marketOpen").value(true));

        long buy = acceptedFor("RELIANCE.NS", "BUY", "2", "inr-buy");
        service.executeOrder(buy);
        assertMoney("9975.7758", cash());
        assertMoney("1001", db.queryForObject("SELECT executed_price FROM fills", BigDecimal.class));
        assertMoney("0.0121", db.queryForObject("SELECT fx_rate FROM fills", BigDecimal.class));
        assertMoney("24.2242", db.queryForObject("SELECT settlement_amount FROM fills", BigDecimal.class));
        assertMoney("12.1121", db.queryForObject("SELECT average_cost FROM account_holdings", BigDecimal.class));
        assertTrue(db.queryForObject("SELECT count(*) FROM audit_logs WHERE state_after LIKE '%FX:INRUSD%'", Integer.class) >= 2);

        long sell = acceptedFor("RELIANCE.NS", "SELL", "1", "inr-sell");
        service.executeOrder(sell);
        assertMoney("9987.6758", cash());
        assertMoney("12.1121", db.queryForObject("SELECT average_cost FROM account_holdings", BigDecimal.class));

        // Cash is fully explained by the account-currency ledger
        assertMoney("9987.6758", db.queryForObject("""
                SELECT sum(CASE transaction_type WHEN 'DEPOSIT' THEN net_amount WHEN 'SELL' THEN net_amount ELSE -net_amount END)
                FROM transactions""", BigDecimal.class));

        long fx = acceptedFor("FX:EURUSD", "BUY", "1000", "eur-buy");
        service.executeOrder(fx);
        assertEquals("FILLED", orderStatus(fx));
        assertMoney("8877.6758", cash());
    }

    @Test void operationsCanTraceAnyClientsTradeAndEveryLookupIsLogged() throws Exception {
        long id = accepted("BUY", "3", "traced");
        service.executeOrder(id);
        String ops = staffToken("OPERATIONS");
        String analyst = staffToken("ANALYST");

        mvc.perform(get("/api/v1/ops/orders/" + id).header("Authorization", bearer())).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/ops/orders/" + id).header("Authorization", "Bearer " + analyst)).andExpect(status().isForbidden());
        // Staff identities cannot trade or read a client book through client endpoints
        mvc.perform(get("/api/v1/orders").header("Authorization", "Bearer " + ops)).andExpect(status().isForbidden());

        mvc.perform(get("/api/v1/ops/orders/" + id).header("Authorization", "Bearer " + ops))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.clientId").value(clientId))
                .andExpect(jsonPath("$.data.summary.order.status").value("FILLED"))
                .andExpect(jsonPath("$.data.fills[0].executedPrice").value(101))
                .andExpect(jsonPath("$.data.ledger[0].netAmount").value(303))
                .andExpect(jsonPath("$.data.timeline.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(6)));
        mvc.perform(get("/api/v1/ops/orders?q=AAPL&status=filled").header("Authorization", "Bearer " + ops))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].order.orderId").value(id));
        mvc.perform(get("/api/v1/ops/orders/999999").header("Authorization", "Bearer " + ops)).andExpect(status().isNotFound());

        assertEquals(2, count("staff_access_log"));
        assertThrows(RuntimeException.class, () -> db.update("DELETE FROM staff_access_log"));
    }

    @Test void clientsKeepAPrivateWatchlist() throws Exception {
        mvc.perform(put("/api/v1/watchlist/X:BTC-USD").header("Authorization", bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].instrument.symbol").value("X:BTC-USD"));
        mvc.perform(put("/api/v1/watchlist/X:BTC-USD").header("Authorization", bearer()))
                .andExpect(jsonPath("$.data.length()").value(1));
        String other = registerAndSignIn("100").path("accessToken").asText();
        mvc.perform(get("/api/v1/watchlist").header("Authorization", "Bearer " + other)).andExpect(jsonPath("$.data.length()").value(0));
        mvc.perform(put("/api/v1/watchlist/NOPE").header("Authorization", bearer())).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/watchlist/X:BTC-USD").header("Authorization", bearer()))
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test void valuationMarksHoldingsAtTheBidAndReportsReturnAgainstDeposits() throws Exception {
        long id = accepted("BUY", "5", "valued");
        service.executeOrder(id);
        mvc.perform(get("/api/v1/portfolio/summary").header("Authorization", bearer()))
                .andExpect(jsonPath("$.data.netDeposits").value(10000))
                .andExpect(jsonPath("$.data.positions[0].averageCost").value(101));
        mvc.perform(get("/api/v1/portfolio/valuation").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.holdingsValue").value(500))
                .andExpect(jsonPath("$.data.totalValue").value(9995))
                .andExpect(jsonPath("$.data.totalReturn").value(-5))
                .andExpect(jsonPath("$.data.positions[0].unrealizedReturn").value(-5))
                .andExpect(jsonPath("$.data.complete").value(true));
    }

    @Test void signInRequiresTheRightPassword() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        mvc.perform(post("/api/v1/auth/register").contentType("application/json")
                .content("{\"name\":\"Test\",\"email\":\"" + email + "\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/auth/register").contentType("application/json")
                .content("{\"name\":\"Test\",\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/auth/sign-in").contentType("application/json")
                .content("{\"email\":\"" + email + "\",\"password\":\"not-the-password\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/sign-in").contentType("application/json").content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isBadRequest());
        assertTrue(db.queryForObject("SELECT password_hash FROM client_profiles WHERE email_address=?", String.class, email).startsWith("$2"));
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
    static final String PASSWORD = "test-password-123";
    private JsonNode registerAndSignIn(String cash) throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        mvc.perform(post("/api/v1/auth/register").contentType("application/json").content("{\"name\":\"Test\",\"email\":\""+email+"\",\"password\":\""+PASSWORD+"\",\"initialCash\":"+cash+"}"))
                .andExpect(status().isCreated());
        return signIn(email);
    }
    private JsonNode signIn(String email) throws Exception {
        var result = mvc.perform(post("/api/v1/auth/sign-in").contentType("application/json").content("{\"email\":\""+email+"\",\"password\":\""+PASSWORD+"\"}"))
                .andExpect(status().isOk()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString()).path("data");
    }
    private String staffToken(String role) throws Exception {
        String email = role.toLowerCase() + "-" + UUID.randomUUID() + "@rocket.internal";
        mvc.perform(post("/api/v1/auth/register").contentType("application/json").content("{\"name\":\"Staff\",\"email\":\""+email+"\",\"password\":\""+PASSWORD+"\"}"))
                .andExpect(status().isCreated());
        db.update("UPDATE client_profiles SET user_role=? WHERE email_address=?", role, email);
        return signIn(email).path("accessToken").asText();
    }
    private long acceptedFor(String symbol, String side, String quantity, String key) throws Exception {
        var response = mvc.perform(post("/api/v1/orders").header("Authorization", bearer()).header("Idempotency-Key", key)
                .contentType("application/json").content("{\"symbol\":\""+symbol+"\",\"side\":\""+side+"\",\"quantity\":"+quantity+"}"))
                .andExpect(status().isAccepted()).andReturn();
        return mapper.readTree(response.getResponse().getContentAsString()).path("data").path("orderId").asLong();
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
    // Ledger entries created by fills, excluding the opening deposit
    private int tradeLedgerEntries() { return db.queryForObject("SELECT count(*) FROM transactions WHERE fill_id IS NOT NULL", Integer.class); }
    private int count(String table) { return db.queryForObject("SELECT count(*) FROM " + table, Integer.class); }
    private BigDecimal cash() { return db.queryForObject("SELECT cash_balance FROM client_accounts WHERE client_id=?", BigDecimal.class, clientId); }
    private void assertMoney(String expected, BigDecimal actual) { assertEquals(0, new BigDecimal(expected).compareTo(actual)); }
    private Quote quote(String symbol, String bid, String ask, Instant at) { return new Quote(symbol, new BigDecimal(bid), new BigDecimal(ask), new BigDecimal(ask), 100, 100, at); }
}
