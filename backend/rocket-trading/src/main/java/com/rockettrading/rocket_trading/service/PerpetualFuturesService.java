package com.rockettrading.rocket_trading.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rockettrading.rocket_trading.dto.perps.*;
import com.rockettrading.rocket_trading.exception.ApiException;
import com.rockettrading.rocket_trading.exception.ConflictException;
import com.rockettrading.rocket_trading.exception.NotFoundException;
import com.rockettrading.rocket_trading.futures.*;
import com.rockettrading.rocket_trading.model.Quote;
import com.rockettrading.rocket_trading.repository.*;
import com.rockettrading.rocket_trading.repository.model.*;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;

/**
 * Isolated-margin perpetual futures. Collateral is the DIRECT_TRADING cash balance; every mutation locks
 * that account row first (as spot settlement does), then the market row (fills only), then the position row,
 * so they never deadlock.
 *
 * <p>Prices come from the spot index plus a premium set by this exchange's long/short open-interest imbalance
 * (see {@link PerpetualFuturesCalculator}); funding charges that premium to the crowded side.
 */
@Service
@RequiredArgsConstructor
public class PerpetualFuturesService {
    private static final Logger log = LoggerFactory.getLogger(PerpetualFuturesService.class);

    private final PerpMarketRepository marketRepository;
    private final PerpPositionRepository positionRepository;
    private final PerpOrderRepository orderRepository;
    private final PerpFundingRepository fundingRepository;
    private final ClientAccountRepository clientAccountRepository;
    private final AuditLogRepository auditLogRepository;
    private final PerpIndexPrices indexPrices;
    private final CoinbaseCandleClient candleClient;
    private final ApplicationEventPublisher events;
    private final Validator validator;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<RateLimitService> rateLimitService;
    private final PerpFillEngine fillEngine = new PerpFillEngine();
    private final PerpetualFuturesCalculator calculator = new PerpetualFuturesCalculator();

    public List<PerpMarketResponse> listMarkets() {
        return marketRepository.findActive().stream()
                .map(market -> new PerpMarketResponse(market.getMarketSymbol(), market.getBaseAsset(),
                        market.getUnderlyingSymbol(), market.getQuoteCurrency(), market.getMaxLeverage(),
                        market.getTakerFeeRate(), spec(market).maintenanceMarginRate(), plain(market.getSkewScale()),
                        PerpetualFuturesCalculator.MAX_PREMIUM))
                .toList();
    }

    public PerpTickerResponse getTicker(String symbol) {
        PerpMarketRecord market = requireMarket(symbol);
        return ticker(market, openInterest(market.getMarketId()));
    }

    /** Tickers for every active market whose price is available right now. */
    public List<PerpTickerResponse> listTickers() {
        Map<Long, PerpOpenInterestRecord> openInterest = openInterestByMarket();
        List<PerpTickerResponse> tickers = new ArrayList<>();
        for (PerpMarketRecord market : marketRepository.findActive()) {
            try {
                tickers.add(ticker(market, openInterest(openInterest, market.getMarketId())));
            } catch (ApiException | IllegalArgumentException unavailable) {
                log.debug("No ticker for {}: {}", market.getMarketSymbol(), unavailable.getMessage());
            }
        }
        return tickers;
    }

    private PerpTickerResponse ticker(PerpMarketRecord market, PerpOpenInterestRecord openInterest) {
        MarketPrice price = marketPrice(market, openInterest);
        Quote quote = price.index().quote();
        PerpMarketSpec spec = spec(market);
        Instant now = Instant.now();
        return new PerpTickerResponse(
                market.getMarketSymbol(),
                plain(price.mark()),
                plain(price.indexPrice()),
                plain(calculator.withPremium(quote.getBid(), price.premium())),
                plain(calculator.withPremium(quote.getAsk(), price.premium())),
                plain(price.premium()),
                calculator.fundingRate(price.mark(), price.indexPrice()),
                calculator.nextFundingTime(now),
                plain(openInterest.total()),
                plain(openInterest.getLongSize()),
                plain(openInterest.getShortSize()),
                spec.maxLeverage(),
                spec.takerFeeRate(),
                spec.maintenanceMarginRate(),
                quote.getCapturedAt(),
                !price.index().live() || quote.isProviderStale()
        );
    }

    public List<PerpCandleResponse> getCandles(String symbol, int intervalSeconds) {
        PerpMarketRecord market = requireMarket(symbol);
        String product = CoinbaseCandleClient.productId(market.getUnderlyingSymbol());
        if (product == null) throw new NotFoundException("PRICE_HISTORY_UNAVAILABLE", "No price history for " + market.getMarketSymbol());
        return candleClient.candles(product, intervalSeconds);
    }

    public PerpAccountResponse getAccount(long clientId) {
        ClientAccountRecord account = clientAccountRepository.findDirectTradingAccountByClientId(clientId);
        if (account == null) throw new NotFoundException("ACCOUNT_NOT_FOUND", "No direct trading account exists for the signed-in client");

        Map<Long, PerpMarketRecord> marketsById = new HashMap<>();
        marketRepository.findAll().forEach(market -> marketsById.put(market.getMarketId(), market));
        Map<Long, PerpOpenInterestRecord> openInterest = openInterestByMarket();
        Map<Long, Optional<BigDecimal>> marks = new HashMap<>();

        BigDecimal totalMargin = BigDecimal.ZERO;
        BigDecimal unrealized = BigDecimal.ZERO;
        List<PerpPositionResponse> responses = new ArrayList<>();
        for (PerpPositionRecord record : positionRepository.findOpenByClientId(clientId)) {
            PerpMarketRecord market = marketsById.get(record.getMarketId());
            BigDecimal mark = marks.computeIfAbsent(record.getMarketId(),
                    id -> markIfAvailable(market, openInterest(openInterest, id))).orElse(null);
            PerpPosition position = toDomain(record);
            totalMargin = totalMargin.add(position.margin());
            if (mark != null) unrealized = unrealized.add(position.unrealizedPnl(mark));
            responses.add(new PerpPositionResponse(
                    record.getPositionId(),
                    record.getMarketSymbol(),
                    record.getPositionSide(),
                    position.size(),
                    plain(position.entryPrice()),
                    mark,
                    mark == null ? null : position.notional(mark),
                    mark == null ? null : position.unrealizedPnl(mark),
                    mark == null ? null : position.returnOnEquity(mark),
                    position.liquidationPrice(spec(market).maintenanceMarginRate()),
                    position.margin(),
                    position.leverage(),
                    record.getCumulativeFunding(),
                    record.getOpenedAt()
            ));
        }
        return new PerpAccountResponse(account.getAccountId(), account.getCurrency(), account.getCashBalance(),
                totalMargin, unrealized, account.getCashBalance().add(totalMargin).add(unrealized), responses);
    }

    // Business rejections return normally so their records commit; the controller turns them into HTTP 409.
    @Transactional
    public PerpOrderResponse submitOrder(long clientId, SubmitPerpOrderRequest request, String key) {
        var violations = validator.validate(request);
        if (!violations.isEmpty()) throw new IllegalArgumentException(violations.iterator().next().getMessage());
        if (key == null || !key.matches("[A-Za-z0-9._:-]{1,100}")) {
            throw new IllegalArgumentException("Idempotency-Key is required (1-100 letters, digits, '.', '_', ':', '-')");
        }
        RateLimitService rateLimiter = rateLimitService.getIfAvailable();
        if (rateLimiter != null && !rateLimiter.isAllowed(clientId)) {
            throw new IllegalArgumentException("Rate limit exceeded: maximum 100 orders per minute");
        }

        ClientAccountRecord account = requireLockedAccount(clientId);
        PerpOrderRecord existing = orderRepository.findByIdempotencyKey(clientId, key);
        if (existing != null) {
            if (!request.fingerprint().equals(existing.getRequestFingerprint())) {
                throw new ConflictException("IDEMPOTENCY_CONFLICT", "Key was already used for different order details");
            }
            return PerpOrderResponse.from(existing);
        }
        PerpMarketRecord market = requireMarket(request.symbol());

        PerpOrderRecord order = new PerpOrderRecord();
        order.setClientId(clientId);
        order.setAccountId(account.getAccountId());
        order.setMarketId(market.getMarketId());
        order.setOrderSide(request.side());
        order.setOrderKind("MARKET");
        order.setRequestedSize(request.size());
        order.setLeverage(request.leverage());
        order.setReduceOnly(request.reduceOnly());
        order.setIdempotencyKey(key);
        order.setRequestFingerprint(request.fingerprint());
        order.setFee(BigDecimal.ZERO);
        order.setRealizedPnl(BigDecimal.ZERO);

        try {
            if (!account.getCurrency().equals(market.getQuoteCurrency())) {
                throw new IllegalArgumentException("Currency conversion is not supported");
            }
            PerpSide side = PerpSide.fromOrderSide(request.side());
            marketRepository.lockMarket(market.getMarketId());
            PerpPositionRecord current = positionRepository.lockOpenPosition(account.getAccountId(), market.getMarketId());
            Quote quote = indexPrices.quote(market).quote();
            // Market orders take liquidity: buys lift the index ask, sells hit the index bid, and both pay the
            // average premium over the skew before and after the fill
            BigDecimal filledSize = request.reduceOnly() && current != null && !current.getPositionSide().equals(side.name())
                    ? request.size().min(current.getSize()) : request.size();
            BigDecimal fillPremium = calculator.fillPremium(openInterest(market.getMarketId()).skew(),
                    side == PerpSide.LONG ? filledSize : filledSize.negate(), market.getSkewScale());
            BigDecimal price = calculator.withPremium(side == PerpSide.LONG ? quote.getAsk() : quote.getBid(), fillPremium);
            PerpFillOutcome outcome = fillEngine.fill(spec(market), current == null ? null : toDomain(current),
                    side, request.size(), price, request.leverage(), request.reduceOnly());

            BigDecimal newCash = account.getCashBalance().add(outcome.cashDelta());
            if (newCash.signum() < 0) {
                throw new IllegalArgumentException("Available collateral cannot cover the required margin and fees");
            }
            if (newCash.setScale(4, RoundingMode.HALF_UP).precision() > 16) {
                throw new IllegalArgumentException("Resulting cash balance exceeds supported precision");
            }

            long positionId = persistFill(account, market, current, outcome);
            clientAccountRepository.updateCashBalance(account.getAccountId(), newCash);

            order.setPositionId(positionId);
            order.setOrderStatus("FILLED");
            order.setFilledSize(outcome.filledSize());
            order.setFillPrice(price);
            order.setFee(outcome.fee());
            order.setRealizedPnl(outcome.realizedPnl());
            orderRepository.insert(order);
            audit("client_accounts", account.getAccountId(), clientId, Map.of("cash", account.getCashBalance()),
                    Map.of("cash", newCash, "perpOrderId", order.getPerpOrderId()));
            events.publishEvent(new PerpMarketChanged(market.getMarketSymbol()));
        } catch (ApiException | IllegalArgumentException rejection) {
            order.setOrderStatus("REJECTED");
            order.setRejectionReason(rejection.getMessage());
            orderRepository.insert(order);
        }
        audit("perp_orders", order.getPerpOrderId(), clientId, null,
                Map.of("status", order.getOrderStatus(), "request", request));
        return PerpOrderResponse.from(orderRepository.findById(order.getPerpOrderId()));
    }

    public List<PerpOrderResponse> listOrders(long clientId) {
        return orderRepository.findByClientId(clientId).stream().map(PerpOrderResponse::from).toList();
    }

    public List<PerpFundingResponse> listFunding(long clientId) {
        return fundingRepository.findByClientId(clientId).stream().map(PerpFundingResponse::from).toList();
    }

    /** The current hour (UTC); positions held over its start are charged once for it. */
    public Instant currentFundingTime() {
        return calculator.fundingTime(Instant.now());
    }

    public List<PerpPositionRecord> positionsDueFunding(Instant fundingTime) {
        return positionRepository.findPositionsDueFunding(fundingTime);
    }

    /**
     * Charges one hour of funding to one position, at the rate set by how far the perp trades from spot now.
     * Returns false if it was already charged, has closed, or was opened after the funding time.
     */
    @Transactional
    public boolean settleFunding(long positionId, long ownerClientId, Instant fundingTime) {
        requireLockedAccount(ownerClientId);
        PerpPositionRecord record = positionRepository.lockOpenPositionById(positionId);
        if (record == null || record.getOpenedAt().isAfter(fundingTime)) return false;
        PerpMarketRecord market = marketRepository.findById(record.getMarketId());
        MarketPrice price = marketPrice(market, openInterest(market.getMarketId()));
        BigDecimal mark = price.mark();
        BigDecimal rate = calculator.fundingRate(mark, price.indexPrice());
        PerpFunding funding = toDomain(record).applyFunding(mark, rate);

        PerpFundingRecord payment = new PerpFundingRecord();
        payment.setPositionId(positionId);
        payment.setClientId(record.getClientId());
        payment.setMarketId(record.getMarketId());
        payment.setFundingTime(fundingTime);
        payment.setPositionSide(record.getPositionSide());
        payment.setSize(record.getSize());
        payment.setMarkPrice(mark);
        payment.setFundingRate(rate);
        payment.setPayment(funding.payment());
        if (fundingRepository.insertIfAbsent(payment) == 0) return false;

        BigDecimal marginBefore = record.getIsolatedMargin();
        record.setIsolatedMargin(funding.position().margin());
        record.setCumulativeFunding(record.getCumulativeFunding().add(funding.payment()));
        positionRepository.update(record);
        audit("perp_positions", positionId, record.getClientId(), Map.of("margin", marginBefore),
                Map.of("margin", record.getIsolatedMargin(), "fundingRate", rate, "fundingTime", fundingTime));
        return true;
    }

    /** Closes the position at mark if equity has fallen to maintenance margin; its isolated margin is forfeited. */
    @Transactional
    public boolean liquidateIfNeeded(long positionId, long ownerClientId) {
        ClientAccountRecord account = requireLockedAccount(ownerClientId);
        PerpPositionRecord record = positionRepository.lockOpenPositionById(positionId);
        if (record == null) return false;
        PerpMarketRecord market = marketRepository.findById(record.getMarketId());
        BigDecimal mark = marketPrice(market, openInterest(market.getMarketId())).mark();
        PerpPosition position = toDomain(record);
        if (!position.isLiquidatable(mark, spec(market).maintenanceMarginRate())) return false;

        BigDecimal loss = position.margin().max(BigDecimal.ZERO).negate();
        record.setPositionStatus("LIQUIDATED");
        record.setRealizedPnl(record.getRealizedPnl().add(loss));
        positionRepository.update(record);

        PerpOrderRecord liquidation = new PerpOrderRecord();
        liquidation.setClientId(record.getClientId());
        liquidation.setAccountId(account.getAccountId());
        liquidation.setMarketId(record.getMarketId());
        liquidation.setPositionId(positionId);
        liquidation.setOrderSide(position.side() == PerpSide.LONG ? "SELL" : "BUY");
        liquidation.setOrderKind("LIQUIDATION");
        liquidation.setRequestedSize(position.size());
        liquidation.setFilledSize(position.size());
        liquidation.setLeverage(position.leverage());
        liquidation.setReduceOnly(true);
        liquidation.setOrderStatus("FILLED");
        liquidation.setFillPrice(mark);
        liquidation.setFee(BigDecimal.ZERO);
        liquidation.setRealizedPnl(loss);
        orderRepository.insert(liquidation);
        events.publishEvent(new PerpMarketChanged(market.getMarketSymbol()));
        audit("perp_positions", positionId, record.getClientId(), Map.of("status", "OPEN", "margin", position.margin()),
                Map.of("status", "LIQUIDATED", "markPrice", mark, "perpOrderId", liquidation.getPerpOrderId()));
        return true;
    }

    public List<PerpPositionRecord> openPositionOwners() {
        return positionRepository.findOpenPositionOwners();
    }

    private long persistFill(ClientAccountRecord account, PerpMarketRecord market, PerpPositionRecord current,
                             PerpFillOutcome outcome) {
        PerpPosition next = outcome.position();
        if (current != null) {
            Map<String, Object> before = snapshot(current);
            boolean sameDirection = next != null && next.side().name().equals(current.getPositionSide());
            current.setRealizedPnl(current.getRealizedPnl().add(outcome.realizedPnl()));
            if (sameDirection) {
                apply(current, next);
            } else {
                current.setPositionStatus("CLOSED");
            }
            positionRepository.update(current);
            audit("perp_positions", current.getPositionId(), account.getClientId(), before, snapshot(current));
            if (sameDirection || next == null) return current.getPositionId();
        }
        // New position, or the opposite-side remainder of a flip
        PerpPositionRecord opened = new PerpPositionRecord();
        opened.setClientId(account.getClientId());
        opened.setAccountId(account.getAccountId());
        opened.setMarketId(market.getMarketId());
        apply(opened, next);
        positionRepository.insert(opened);
        audit("perp_positions", opened.getPositionId(), account.getClientId(), null, snapshot(opened));
        return opened.getPositionId();
    }

    private void apply(PerpPositionRecord record, PerpPosition position) {
        record.setPositionSide(position.side().name());
        record.setSize(position.size());
        record.setEntryPrice(position.entryPrice());
        record.setIsolatedMargin(position.margin());
        record.setLeverage(position.leverage());
        record.setPositionStatus("OPEN");
    }

    private Map<String, Object> snapshot(PerpPositionRecord record) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("status", record.getPositionStatus());
        state.put("side", record.getPositionSide());
        state.put("size", record.getSize());
        state.put("entryPrice", record.getEntryPrice());
        state.put("margin", record.getIsolatedMargin());
        state.put("leverage", record.getLeverage());
        return state;
    }

    private Optional<BigDecimal> markIfAvailable(PerpMarketRecord market, PerpOpenInterestRecord openInterest) {
        try {
            return Optional.of(plain(marketPrice(market, openInterest).mark()));
        } catch (ApiException | IllegalArgumentException unavailable) {
            log.warn("Mark price unavailable for {}: {}", market.getMarketSymbol(), unavailable.getMessage());
            return Optional.empty();
        }
    }

    /** Index is the spot quote mid; mark adds the premium the open-interest skew sets. */
    private MarketPrice marketPrice(PerpMarketRecord market, PerpOpenInterestRecord openInterest) {
        PerpIndexPrices.IndexQuote index = indexPrices.quote(market);
        BigDecimal indexPrice = calculator.indexPrice(index.quote().getBid(), index.quote().getAsk());
        BigDecimal premium = calculator.premium(openInterest.skew(), market.getSkewScale());
        return new MarketPrice(index, indexPrice, premium, calculator.withPremium(indexPrice, premium));
    }

    private record MarketPrice(PerpIndexPrices.IndexQuote index, BigDecimal indexPrice, BigDecimal premium, BigDecimal mark) {
    }

    private Map<Long, PerpOpenInterestRecord> openInterestByMarket() {
        Map<Long, PerpOpenInterestRecord> byMarket = new HashMap<>();
        marketRepository.openInterest().forEach(record -> byMarket.put(record.getMarketId(), record));
        return byMarket;
    }

    private PerpOpenInterestRecord openInterest(long marketId) {
        return openInterest(openInterestByMarket(), marketId);
    }

    private static PerpOpenInterestRecord openInterest(Map<Long, PerpOpenInterestRecord> byMarket, long marketId) {
        return byMarket.getOrDefault(marketId, PerpOpenInterestRecord.none(marketId));
    }

    private PerpMarketRecord requireMarket(String symbol) {
        PerpMarketRecord market = symbol == null ? null : marketRepository.findActiveBySymbol(symbol.trim().toUpperCase(Locale.ROOT));
        if (market == null) throw new NotFoundException("PERP_MARKET_NOT_FOUND", "Perpetual market " + symbol + " is not listed");
        return market;
    }

    private ClientAccountRecord requireLockedAccount(long clientId) {
        ClientAccountRecord account = clientAccountRepository.lockDirectTradingAccount(clientId);
        if (account == null) throw new NotFoundException("ACCOUNT_NOT_FOUND", "No direct trading account exists for the signed-in client");
        return account;
    }

    private static PerpMarketSpec spec(PerpMarketRecord market) {
        return new PerpMarketSpec(market.getMarketSymbol(), market.getMaxLeverage(), market.getTakerFeeRate());
    }

    private static PerpPosition toDomain(PerpPositionRecord record) {
        return new PerpPosition(PerpSide.valueOf(record.getPositionSide()), record.getSize(), record.getEntryPrice(),
                record.getIsolatedMargin(), record.getLeverage());
    }

    // Avoids exponent notation (1E+2) in JSON for whole-number prices
    private static BigDecimal plain(BigDecimal value) {
        BigDecimal stripped = value.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
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
            throw new IllegalStateException("Could not serialize perpetual futures audit", exception);
        }
    }
}
