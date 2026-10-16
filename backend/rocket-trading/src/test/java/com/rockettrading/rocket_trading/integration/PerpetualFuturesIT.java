package com.rockettrading.rocket_trading.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rockettrading.rocket_trading.dto.perps.PerpCandleResponse;
import com.rockettrading.rocket_trading.exception.ExternalServiceException;
import com.rockettrading.rocket_trading.model.Quote;
import com.rockettrading.rocket_trading.service.CoinbaseCandleClient;
import com.rockettrading.rocket_trading.service.CoinbaseTickerFeed;
import com.rockettrading.rocket_trading.service.PerpetualFuturesSettlement;
import com.rockettrading.rocket_trading.service.QuoteProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
// Same isolation as TradingIntegrationIT: no Kafka/Redis, no fixtures, and quote freshness pinned against the developer's .env
@SpringBootTest(properties = {"trading.worker.enabled=false", "spring.kafka.enabled=false", "spring.redis.enabled=false", "reporting.refresh-ms=3600000", "fixtures.password=", "catalog.sync.enabled=false",
        "quotes.fauxnance.max-age-seconds=900",
        // The live feed is wired up but points at a closed port, so prices fall back to the mocked provider
        "trading.perps.price-feed.enabled=true", "trading.perps.price-feed.websocket-url=ws://127.0.0.1:9",
        // No timed pushes, so the stream test sees only the push a trade causes
        "trading.perps.stream-interval-ms=3600000"})
@AutoConfigureMockMvc
class PerpetualFuturesIT {
    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate db;
    @Autowired PerpetualFuturesSettlement settlement;
    @MockitoBean QuoteProvider quotes;
    @Autowired ObjectProvider<CoinbaseTickerFeed> feed;

    // A subclass rather than a Mockito class mock, which newer JDKs cannot instrument
    @TestConfiguration
    static class FakeCandles {
        @Bean @Primary
        CoinbaseCandleClient fakeCandleClient() {
            return new CoinbaseCandleClient(RestClient.create()) {
                @Override
                public List<PerpCandleResponse> candles(String productId, int granularitySeconds) {
                    return List.of(new PerpCandleResponse(1792011600L, new BigDecimal("2440"), new BigDecimal("2450"),
                            new BigDecimal("2435"), new BigDecimal(productId.equals("ETH-USD") && granularitySeconds == 300 ? "2442" : "0")));
                }
            };
        }
    }
    static final String PASSWORD = "test-password-123";
    String token;
    long clientId;

    @BeforeEach void setUp() throws Exception {
        // Only this disposable container is reset; never use a developer database in tests.
        db.execute("TRUNCATE perp_funding_payments, perp_orders, perp_positions, staff_access_log, client_watchlist, audit_logs, transactions, fills, orders, market_quotes, account_holdings, client_holdings, client_sessions, client_accounts, client_profiles RESTART IDENTITY CASCADE");
        // bid 100 / ask 101 -> index (mid) 100.5. BTC-PERP's skew scale is 1000, so each BTC of net long open
        // interest adds a 0.1% premium, and a fill pays the average premium before and after it
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
                .andExpect(jsonPath("$.data[0].skewScale").value(1000))
                .andExpect(jsonPath("$.data[0].maxPremium").value(0.01))
                .andExpect(jsonPath("$.data[1].symbol").value("ETH-PERP"))
                .andExpect(jsonPath("$.data[1].skewScale").value(30000));
    }

    @Test void tickerWithNoOpenInterestTradesAtTheIndexAndChargesNoFunding() throws Exception {
        quoteAt("100", "102", "100");
        mvc.perform(get("/api/v1/perps/markets/BTC-PERP/ticker").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.oraclePrice").value(101))
                .andExpect(jsonPath("$.data.markPrice").value(101))
                .andExpect(jsonPath("$.data.premium").value(0))
                .andExpect(jsonPath("$.data.bid").value(100))
                .andExpect(jsonPath("$.data.ask").value(102))
                .andExpect(jsonPath("$.data.fundingRate").value(0))
                .andExpect(jsonPath("$.data.nextFundingAt").exists())
                .andExpect(jsonPath("$.data.openInterest").value(0))
                // Only the live stream counts as current; the mocked provider is the fallback
                .andExpect(jsonPath("$.data.delayed").value(true));
    }

    @Test void longOpenInterestPutsTheMarkAtAPremiumThatLongsPayAsFunding() throws Exception {
        order("BUY", "2", 10, false, "skew-long").andExpect(status().isCreated());
        String otherToken = registerAndSignIn("10000").path("accessToken").asText();
        mvc.perform(post("/api/v1/perps/orders").header("Authorization", "Bearer " + otherToken)
                        .header("Idempotency-Key", "skew-short").contentType("application/json").content(body("SELL", "1", 10, false)))
                .andExpect(status().isCreated())
                // Selling into a 2 BTC long skew: premium (0.002 + 0.001) / 2 on the bid of 100
                .andExpect(jsonPath("$.data.fillPrice").value(100.15));

        quoteAt("100", "102", "100");
        // Skew 1 BTC long -> premium 0.1%: mark 101 * 1.001; funding (mark - spot) / spot / 8 an hour
        mvc.perform(get("/api/v1/perps/markets/BTC-PERP/ticker").header("Authorization", bearer()))
                .andExpect(jsonPath("$.data.premium").value(0.001))
                .andExpect(jsonPath("$.data.markPrice").value(101.101))
                .andExpect(jsonPath("$.data.bid").value(100.1))
                .andExpect(jsonPath("$.data.ask").value(102.102))
                .andExpect(jsonPath("$.data.fundingRate").value(0.000125))
                .andExpect(jsonPath("$.data.openInterest").value(3))
                .andExpect(jsonPath("$.data.longOpenInterest").value(2))
                .andExpect(jsonPath("$.data.shortOpenInterest").value(1));
    }

    @Test void openingAndClosingTheSameSizeHasNoNetPriceImpact() throws Exception {
        quoteAt("100", "100", "100");
        order("BUY", "3", 10, false, "impact-open").andExpect(status().isCreated())
                // (0 + 0.003) / 2 over the index
                .andExpect(jsonPath("$.data.fillPrice").value(100.15));
        mvc.perform(get("/api/v1/perps/account").header("Authorization", bearer()))
                .andExpect(jsonPath("$.data.positions[0].markPrice").value(100.3));
        // Closing pays the same average premium (0.003 + 0) / 2, so the round trip is flat before fees:
        // the trader cannot bank the mark move their own order caused
        order("SELL", "3", 10, true, "impact-close").andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.fillPrice").value(100.15))
                .andExpect(jsonPath("$.data.realizedPnl").value(0));
    }

    @Test void liveFeedIsWiredUpAndPricesFallBackWhileItIsUnreachable() throws Exception {
        assertNotNull(feed.getIfAvailable());
        mvc.perform(get("/api/v1/perps/markets/BTC-PERP/ticker").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.markPrice").value(100.5))
                .andExpect(jsonPath("$.data.delayed").value(true));
    }

        @Test void unknownMarketIs404() throws Exception {
        mvc.perform(get("/api/v1/perps/markets/DOGE-PERP/ticker").header("Authorization", bearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("PERP_MARKET_NOT_FOUND"));
    }

    @Test void opensLongAtAskDebitingMarginAndFeeThenClosesWithReduceOnly() throws Exception {
        order("BUY", "2", 10, false, "open-1").andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("FILLED"))
                // ask 101 plus the average premium (0 + 0.002) / 2
                .andExpect(jsonPath("$.data.fillPrice").value(101.101))
                .andExpect(jsonPath("$.data.fee").value(0.091));
        // margin 202.202 / 10 = 20.2202, fee 202.202 * 0.045% = 0.0910
        assertMoney("9979.6888", cash());

        mvc.perform(get("/api/v1/perps/account").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.availableCollateral").value(9979.6888))
                .andExpect(jsonPath("$.data.positions", hasSize(1)))
                .andExpect(jsonPath("$.data.positions[0].symbol").value("BTC-PERP"))
                .andExpect(jsonPath("$.data.positions[0].side").value("LONG"))
                .andExpect(jsonPath("$.data.positions[0].size").value(2))
                .andExpect(jsonPath("$.data.positions[0].entryPrice").value(101.101))
                // index 100.5 at the 0.2% premium the 2 BTC long skew sets
                .andExpect(jsonPath("$.data.positions[0].markPrice").value(100.701))
                .andExpect(jsonPath("$.data.positions[0].unrealizedPnl").value(-0.8))
                .andExpect(jsonPath("$.data.positions[0].margin").value(20.2202))
                .andExpect(jsonPath("$.data.positions[0].leverage").value(10))
                .andExpect(jsonPath("$.data.positions[0].liquidationPrice").exists())
                .andExpect(jsonPath("$.data.totalMargin").value(20.2202))
                .andExpect(jsonPath("$.data.accountValue").value(9999.109));

        quoteAt("110", "111", "110.5");
        // Selling closes at the bid plus the average premium (0.002 + 0) / 2: realized (110.11 - 101.101) * 2
        order("SELL", "5", 10, true, "close-1").andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.filledSize").value(2))
                .andExpect(jsonPath("$.data.fillPrice").value(110.11))
                .andExpect(jsonPath("$.data.realizedPnl").value(18.018));
        // 9979.6888 + 20.2202 + 18.018 - 220.22 * 0.045% (0.0991)
        assertMoney("10017.8279", cash());
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
        // cash 9979.6888 + perp equity (margin 20.2202 + unrealized -0.8 at mark 100.701) = 9999.109
        mvc.perform(get("/api/v1/portfolio/valuation").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.perpsValue").value(19.42))
                .andExpect(jsonPath("$.data.totalValue").value(9999.11))
                // Only the fee (0.091) and the unrealized -0.8 count against the return
                .andExpect(jsonPath("$.data.totalReturn").value(-0.89));
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
        // 200 BTC at 101 (plus a 0.5% average premium) / 2x needs 10,150.5 margin against 10,000 cash
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

    @Test void fundingFollowsTheSkewAndSettlesOncePerIntervalForPositionsHeldOverIt() throws Exception {
        order("BUY", "2", 10, false, "funded").andExpect(status().isCreated());
        String otherToken = registerAndSignIn("10000").path("accessToken").asText();
        mvc.perform(post("/api/v1/perps/orders").header("Authorization", "Bearer " + otherToken)
                        .header("Idempotency-Key", "funded-short").contentType("application/json").content(body("SELL", "1", 10, false)))
                .andExpect(status().isCreated());
        // Both opened after the current interval started, so neither owes it
        assertEquals(0, settlement.settleCurrentFunding());

        db.update("UPDATE perp_positions SET opened_at = opened_at - interval '2 hours'");
        quoteAt("100", "102", "100");
        // Skew 1 BTC long -> premium 0.1%, mark 101.101, hourly rate 0.1% / 8 = 0.000125
        // long pays 2 * 101.101 * rate = 0.0253; short receives 1 * 101.101 * rate = 0.0126
        assertEquals(2, settlement.settleCurrentFunding());
        assertEquals(0, settlement.settleCurrentFunding());

        // margin 202.202 / 10 = 20.2202 less the payment
        assertMoney("20.1949", db.queryForObject("SELECT isolated_margin FROM perp_positions WHERE client_id = ?", BigDecimal.class, clientId));
        assertMoney("0.0253", db.queryForObject("SELECT cumulative_funding FROM perp_positions WHERE client_id = ?", BigDecimal.class, clientId));
        mvc.perform(get("/api/v1/perps/funding").header("Authorization", bearer()))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].symbol").value("BTC-PERP"))
                .andExpect(jsonPath("$.data[0].side").value("LONG"))
                .andExpect(jsonPath("$.data[0].markPrice").value(101.101))
                .andExpect(jsonPath("$.data[0].payment").value(0.0253))
                .andExpect(jsonPath("$.data[0].fundingRate").value(0.000125));
        mvc.perform(get("/api/v1/perps/funding").header("Authorization", "Bearer " + otherToken))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].side").value("SHORT"))
                .andExpect(jsonPath("$.data[0].payment").value(-0.0126));
        mvc.perform(get("/api/v1/perps/account").header("Authorization", bearer()))
                .andExpect(jsonPath("$.data.positions[0].cumulativeFunding").value(0.0253));
    }

    @Test void fundingThatFailsForLackOfAPriceIsRetriedOnTheNextCheck() throws Exception {
        order("BUY", "2", 10, false, "retry").andExpect(status().isCreated());
        db.update("UPDATE perp_positions SET opened_at = opened_at - interval '2 hours'");
        doThrow(new ExternalServiceException("PRICE_UNAVAILABLE", "down")).when(quotes).fetchQuote(anyString(), anyString());
        assertEquals(0, settlement.settleCurrentFunding());
        assertEquals(0, count("perp_funding_payments"));

        quoteAt("100", "101", "100.5");
        assertEquals(1, settlement.settleCurrentFunding());
        mvc.perform(get("/api/v1/perps/funding").header("Authorization", bearer()))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].fundingTime").value(org.hamcrest.Matchers.startsWith(
                        Instant.now().truncatedTo(ChronoUnit.HOURS).toString().replace("Z", ""))));
    }

    @Test void liquidationClosesUnderwaterPositionAndForfeitsItsMargin() throws Exception {
        order("BUY", "1", 20, false, "levered").andExpect(status().isCreated());
        BigDecimal cashAfterOpen = cash();
        // entry 101.0505, margin 5.0525, mmr 1.25% -> liquidation near 97.20; mark 100.6005 is safe
        assertEquals(0, settlement.sweepLiquidations());

        quoteAt("96", "97", "96.5");
        assertEquals(1, settlement.sweepLiquidations());

        assertEquals("LIQUIDATED", db.queryForObject("SELECT position_status FROM perp_positions", String.class));
        assertMoney(cashAfterOpen.toPlainString(), cash());
        mvc.perform(get("/api/v1/perps/orders").header("Authorization", bearer()))
                .andExpect(jsonPath("$.data[0].kind").value("LIQUIDATION"))
                .andExpect(jsonPath("$.data[0].side").value("SELL"))
                .andExpect(jsonPath("$.data[0].realizedPnl").value(-5.0525));
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
                .andExpect(jsonPath("$.data.openInterest").value(3.5))
                .andExpect(jsonPath("$.data.longOpenInterest").value(2))
                .andExpect(jsonPath("$.data.shortOpenInterest").value(1.5));
    }

    @Test void streamPushesEveryMarketsTickerToSignedInClients() throws Exception {
        MvcResult result = mvc.perform(get("/api/v1/perps/stream").header("Authorization", bearer()))
                .andExpect(request().asyncStarted())
                .andReturn();
        assertTrue(result.getResponse().getContentType().startsWith("text/event-stream"));
        String events = result.getResponse().getContentAsString();
        assertTrue(events.startsWith("event:tickers"), events);
        JsonNode tickers = mapper.readTree(events.substring(events.indexOf("data:") + 5).trim());
        assertEquals(2, tickers.size());
        assertEquals("BTC-PERP", tickers.get(0).path("symbol").asText());
        assertEquals(100.5, tickers.get(0).path("markPrice").asDouble());
        assertEquals("ETH-PERP", tickers.get(1).path("symbol").asText());

        mvc.perform(get("/api/v1/perps/stream")).andExpect(status().isUnauthorized());
    }

    @Test void everyTradeIsPushedToTheStreamAsSoonAsItCommits() throws Exception {
        MvcResult stream = mvc.perform(get("/api/v1/perps/stream").header("Authorization", bearer()))
                .andExpect(request().asyncStarted())
                .andReturn();
        assertTrue(stream.getResponse().getContentAsString().contains("\"openInterest\":0"));

        order("BUY", "2", 10, false, "pushed").andExpect(status().isCreated());

        // The timed push is an hour away, so this update can only come from the trade itself
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            String events = stream.getResponse().getContentAsString();
            assertTrue(events.contains("\"longOpenInterest\":2"), events);
            assertTrue(events.contains("\"premium\":0.002"), events);
        });
    }

    @Test void tickersListsEveryMarket() throws Exception {
        mvc.perform(get("/api/v1/perps/tickers").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].symbol").value("BTC-PERP"))
                .andExpect(jsonPath("$.data[1].symbol").value("ETH-PERP"));
    }

    @Test void candlesComeFromTheCoinbaseProductForTheMarket() throws Exception {
        mvc.perform(get("/api/v1/perps/markets/ETH-PERP/candles?interval=300").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].time").value(1792011600L))
                .andExpect(jsonPath("$.data[0].open").value(2440))
                .andExpect(jsonPath("$.data[0].close").value(2442));
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
