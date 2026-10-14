package com.rockettrading.rocket_trading.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rockettrading.rocket_trading.model.Quote;
import com.rockettrading.rocket_trading.service.PerpetualFuturesSettlement;
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
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
// Same isolation as TradingIntegrationIT: no Kafka/Redis, no fixtures, and quote freshness pinned against the developer's .env
@SpringBootTest(properties = {"trading.worker.enabled=false", "spring.kafka.enabled=false", "spring.redis.enabled=false", "reporting.refresh-ms=3600000", "fixtures.password=", "catalog.sync.enabled=false",
        "quotes.fauxnance.max-age-seconds=900"})
@AutoConfigureMockMvc
class PerpetualFuturesIT {
    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate db;
    @Autowired PerpetualFuturesSettlement settlement;
    @MockitoBean QuoteProvider quotes;
    static final String PASSWORD = "test-password-123";
    String token;
    long clientId;

    @BeforeEach void setUp() throws Exception {
        // Only this disposable container is reset; never use a developer database in tests.
        db.execute("TRUNCATE perp_funding_payments, perp_orders, perp_positions, staff_access_log, client_watchlist, audit_logs, transactions, fills, orders, market_quotes, account_holdings, client_holdings, client_sessions, client_accounts, client_profiles RESTART IDENTITY CASCADE");
        // bid 100 / ask 101 -> mark (mid) 100.5, oracle 100.5
        quoteAt("100", "101", "100.5");
        JsonNode session = registerAndSignIn("10000");
        token = session.path("accessToken").asText();
        clientId = session.path("clientId").asLong();
    }

    @Test void listsSeededMarketsWithRiskParameters() throws Exception {
        mvc.perform(get("/api/v1/perps/markets").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].symbol").value("BTC-PERP"))
                .andExpect(jsonPath("$.data[0].underlyingSymbol").value("X:BTC-USD"))
                .andExpect(jsonPath("$.data[0].maxLeverage").value(40))
                .andExpect(jsonPath("$.data[1].symbol").value("ETH-PERP"));
    }

    @Test void tickerUsesQuoteMidAsMarkAndProviderPriceAsOracle() throws Exception {
        quoteAt("100", "102", "100");
        mvc.perform(get("/api/v1/perps/markets/BTC-PERP/ticker").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.markPrice").value(101))
                .andExpect(jsonPath("$.data.oraclePrice").value(100))
                .andExpect(jsonPath("$.data.bid").value(100))
                .andExpect(jsonPath("$.data.ask").value(102))
                .andExpect(jsonPath("$.data.fundingRate").value(0.0011875))
                .andExpect(jsonPath("$.data.nextFundingAt").exists())
                .andExpect(jsonPath("$.data.openInterest").value(0));
    }

    @Test void unknownMarketIs404() throws Exception {
        mvc.perform(get("/api/v1/perps/markets/DOGE-PERP/ticker").header("Authorization", bearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("PERP_MARKET_NOT_FOUND"));
    }

    @Test void opensLongAtAskDebitingMarginAndFeeThenClosesWithReduceOnly() throws Exception {
        order("BUY", "2", 10, false, "open-1").andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("FILLED"))
                .andExpect(jsonPath("$.data.fillPrice").value(101))
                .andExpect(jsonPath("$.data.fee").value(0.0909));
        // margin 202 / 10 = 20.2, fee 202 * 0.045% = 0.0909
        assertMoney("9979.7091", cash());

        mvc.perform(get("/api/v1/perps/account").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.availableCollateral").value(9979.7091))
                .andExpect(jsonPath("$.data.positions", hasSize(1)))
                .andExpect(jsonPath("$.data.positions[0].symbol").value("BTC-PERP"))
                .andExpect(jsonPath("$.data.positions[0].side").value("LONG"))
                .andExpect(jsonPath("$.data.positions[0].size").value(2))
                .andExpect(jsonPath("$.data.positions[0].entryPrice").value(101))
                .andExpect(jsonPath("$.data.positions[0].markPrice").value(100.5))
                .andExpect(jsonPath("$.data.positions[0].unrealizedPnl").value(-1))
                .andExpect(jsonPath("$.data.positions[0].margin").value(20.2))
                .andExpect(jsonPath("$.data.positions[0].leverage").value(10))
                .andExpect(jsonPath("$.data.positions[0].liquidationPrice").exists())
                .andExpect(jsonPath("$.data.totalMargin").value(20.2))
                .andExpect(jsonPath("$.data.accountValue").value(9998.9091));

        quoteAt("110", "111", "110.5");
        // Selling closes at the bid: realized (110 - 101) * 2 = 18
        order("SELL", "5", 10, true, "close-1").andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.filledSize").value(2))
                .andExpect(jsonPath("$.data.realizedPnl").value(18));
        // 9979.7091 + 20.2 + 18 - 220 * 0.045% (0.099)
        assertMoney("10017.8101", cash());
        mvc.perform(get("/api/v1/perps/account").header("Authorization", bearer()))
                .andExpect(jsonPath("$.data.positions", hasSize(0)));
        mvc.perform(get("/api/v1/perps/orders").header("Authorization", bearer()))
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].side").value("SELL"));
    }

    @Test void perpTradingLeavesSpotHoldingsAndLedgerUntouched() throws Exception {
        // Registration records the opening DEPOSIT; perp trading must add nothing to the spot ledger
        int ledgerBefore = count("transactions");
        order("SELL", "1", 5, false, "short-1").andExpect(status().isCreated());
        assertEquals(0, count("orders"));
        assertEquals(ledgerBefore, count("transactions"));
        assertEquals(0, count("account_holdings"));
        assertTrue(db.queryForObject("SELECT count(*) FROM audit_logs WHERE entity_name = 'perp_positions'", Integer.class) >= 1);
        mvc.perform(get("/api/v1/portfolio/summary").header("Authorization", bearer()))
                .andExpect(jsonPath("$.data.positions", hasSize(0)));
    }

    @Test void dashboardValuationIncludesPerpEquitySoPostedMarginIsNotALoss() throws Exception {
        order("BUY", "2", 10, false, "valued").andExpect(status().isCreated());
        // cash 9979.7091 + perp equity (margin 20.2 + unrealized -1 at mark 100.5) = 9998.9091
        mvc.perform(get("/api/v1/portfolio/valuation").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.perpsValue").value(19.2))
                .andExpect(jsonPath("$.data.totalValue").value(9998.91))
                // Only the fee (0.0909) and the unrealized -1 count against the return
                .andExpect(jsonPath("$.data.totalReturn").value(-1.09));
    }

    @Test void retriesWithSameKeyReturnSameOrderAndDifferentPayloadConflicts() throws Exception {
        long first = orderId(order("BUY", "1", 10, false, "same-key").andExpect(status().isCreated()));
        long retry = orderId(order("BUY", "1", 10, false, "same-key").andExpect(status().isCreated()));
        assertEquals(first, retry);
        assertEquals(1, count("perp_orders"));
        order("BUY", "2", 10, false, "same-key").andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_CONFLICT"));
        mvc.perform(post("/api/v1/perps/orders").header("Authorization", bearer())
                        .contentType("application/json").content(body("BUY", "1", 10, false)))
                .andExpect(status().isBadRequest());
    }

    @Test void insufficientCollateralIsRejectedAndRecordedWithoutMovingCash() throws Exception {
        // 200 BTC at 101 / 2x needs 10,100 margin against 10,000 cash
        order("BUY", "200", 2, false, "too-big").andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ORDER_REJECTED"));
        assertMoney("10000", cash());
        assertEquals("REJECTED", db.queryForObject("SELECT order_status FROM perp_orders", String.class));
        assertEquals(0, count("perp_positions"));
    }

    @Test void leverageAboveMarketMaximumIsRejected() throws Exception {
        order("BUY", "1", 41, false, "lev-41").andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message").value("leverage must be between 1x and 40x"));
        order("BUY", "1", 0, false, "lev-0").andExpect(status().isBadRequest());
    }

    @Test void positionsAreScopedToTheirOwner() throws Exception {
        order("BUY", "1", 10, false, "mine").andExpect(status().isCreated());
        String otherToken = registerAndSignIn("500").path("accessToken").asText();
        mvc.perform(get("/api/v1/perps/account").header("Authorization", "Bearer " + otherToken))
                .andExpect(jsonPath("$.data.positions", hasSize(0)));
        mvc.perform(get("/api/v1/perps/orders").header("Authorization", "Bearer " + otherToken))
                .andExpect(jsonPath("$.data", hasSize(0)));
        mvc.perform(get("/api/v1/perps/account")).andExpect(status().isUnauthorized());
    }

    @Test void fundingSettlesOncePerHourAgainstIsolatedMargin() throws Exception {
        order("BUY", "2", 10, false, "funded").andExpect(status().isCreated());
        // mark 101 vs oracle 100 -> hourly rate 0.0011875; long pays 2 * 101 * rate = 0.239875 -> 0.2399
        quoteAt("100", "102", "100");
        Instant hour = Instant.now().truncatedTo(ChronoUnit.HOURS);

        assertEquals(1, settlement.settleFunding(hour));
        assertEquals(0, settlement.settleFunding(hour));

        assertMoney("19.9601", db.queryForObject("SELECT isolated_margin FROM perp_positions", BigDecimal.class));
        mvc.perform(get("/api/v1/perps/funding").header("Authorization", bearer()))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].symbol").value("BTC-PERP"))
                .andExpect(jsonPath("$.data[0].payment").value(0.2399))
                .andExpect(jsonPath("$.data[0].fundingRate").value(0.0011875));
    }

    @Test void liquidationClosesUnderwaterPositionAndForfeitsItsMargin() throws Exception {
        order("BUY", "1", 20, false, "levered").andExpect(status().isCreated());
        BigDecimal cashAfterOpen = cash();
        // entry 101, margin 5.05, mmr 1.25% -> liquidation near 97.16; mark 100.5 is safe
        assertEquals(0, settlement.sweepLiquidations());

        quoteAt("96", "97", "96.5");
        assertEquals(1, settlement.sweepLiquidations());

        assertEquals("LIQUIDATED", db.queryForObject("SELECT position_status FROM perp_positions", String.class));
        assertMoney(cashAfterOpen.toPlainString(), cash());
        mvc.perform(get("/api/v1/perps/orders").header("Authorization", bearer()))
                .andExpect(jsonPath("$.data[0].kind").value("LIQUIDATION"))
                .andExpect(jsonPath("$.data[0].side").value("SELL"))
                .andExpect(jsonPath("$.data[0].realizedPnl").value(-5.05));
        mvc.perform(get("/api/v1/perps/account").header("Authorization", bearer()))
                .andExpect(jsonPath("$.data.positions", hasSize(0)));
        assertEquals(0, settlement.sweepLiquidations());
    }

    @Test void openInterestSumsOpenPositionSizesAcrossClients() throws Exception {
        order("BUY", "2", 10, false, "oi-1").andExpect(status().isCreated());
        String otherToken = registerAndSignIn("10000").path("accessToken").asText();
        mvc.perform(post("/api/v1/perps/orders").header("Authorization", "Bearer " + otherToken)
                        .header("Idempotency-Key", "oi-2").contentType("application/json").content(body("SELL", "1.5", 10, false)))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/v1/perps/markets/BTC-PERP/ticker").header("Authorization", bearer()))
                .andExpect(jsonPath("$.data.openInterest").value(3.5));
    }

    private ResultActions order(String side, String size, int leverage, boolean reduceOnly, String key) throws Exception {
        return mvc.perform(post("/api/v1/perps/orders").header("Authorization", bearer()).header("Idempotency-Key", key)
                .contentType("application/json").content(body(side, size, leverage, reduceOnly)));
    }
    private String body(String side, String size, int leverage, boolean reduceOnly) {
        return "{\"symbol\":\"BTC-PERP\",\"side\":\"" + side + "\",\"size\":" + size
                + ",\"leverage\":" + leverage + ",\"reduceOnly\":" + reduceOnly + "}";
    }
    private long orderId(ResultActions result) throws Exception {
        return mapper.readTree(result.andReturn().getResponse().getContentAsString()).path("data").path("orderId").asLong();
    }
    private JsonNode registerAndSignIn(String cash) throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        mvc.perform(post("/api/v1/auth/register").contentType("application/json").content("{\"name\":\"Test\",\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\",\"initialCash\":" + cash + "}"))
                .andExpect(status().isCreated());
        var result = mvc.perform(post("/api/v1/auth/sign-in").contentType("application/json").content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString()).path("data");
    }
    private void quoteAt(String bid, String ask, String price) {
        doAnswer(i -> new Quote(i.getArgument(0), new BigDecimal(bid), new BigDecimal(ask), new BigDecimal(price), 100, 100, Instant.now()))
                .when(quotes).fetchQuote(anyString(), anyString());
    }
    private String bearer() { return "Bearer " + token; }
    private int count(String table) { return db.queryForObject("SELECT count(*) FROM " + table, Integer.class); }
    private BigDecimal cash() { return db.queryForObject("SELECT cash_balance FROM client_accounts WHERE client_id=?", BigDecimal.class, clientId); }
    private void assertMoney(String expected, BigDecimal actual) { assertEquals(0, new BigDecimal(expected).compareTo(actual), () -> "expected " + expected + " but was " + actual); }
}
