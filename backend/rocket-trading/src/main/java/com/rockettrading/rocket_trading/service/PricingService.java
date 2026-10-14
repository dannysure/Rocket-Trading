package com.rockettrading.rocket_trading.service;

import com.rockettrading.rocket_trading.model.Quote;
import com.rockettrading.rocket_trading.repository.model.FinancialInstrumentRecord;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Prices an order in the instrument's currency and converts it to the account currency.
 * The same rules serve the indicative price (BR-13), acceptance checks and execution (BR-08).
 */
@Service
public class PricingService {
    private final QuoteService quoteService;

    public PricingService(QuoteService quoteService) {
        this.quoteService = quoteService;
    }

    /** Prices against current quotes only; throws QUOTE_STALE when a market has no current price. */
    public Pricing priceForExecution(FinancialInstrumentRecord instrument, String side, BigDecimal quantity, String accountCurrency) {
        return price(instrument, side, quantity, accountCurrency, true);
    }

    /** Prices against the latest quotes even if a market is closed; {@link Pricing#current()} says which. */
    public Pricing priceIndicative(FinancialInstrumentRecord instrument, String side, BigDecimal quantity, String accountCurrency) {
        return price(instrument, side, quantity, accountCurrency, false);
    }

    private Pricing price(FinancialInstrumentRecord instrument, String side, BigDecimal quantity,
                          String accountCurrency, boolean requireCurrent) {
        String market = instrument.market();
        Quote quote = quoteService.fetchQuote(instrument.getTickerSymbol(), market, requireCurrent);
        boolean current = quoteService.isCurrent(quote, market);
        boolean buying = "BUY".equals(side);

        BigDecimal unitPrice = (buying ? quote.getAsk() : quote.getBid()).setScale(4, RoundingMode.HALF_UP);
        if (unitPrice.signum() <= 0 || unitPrice.precision() > 16) {
            throw new IllegalArgumentException("Quote price is outside supported precision");
        }

        String instrumentCurrency = instrument.getBaseCurrency();
        BigDecimal fxRate = BigDecimal.ONE;
        Quote fxQuote = null;
        if (!instrumentCurrency.equalsIgnoreCase(accountCurrency)) {
            // The client pays the FX spread: ask when converting cash out to buy, bid when converting proceeds back
            String fxSymbol = "FX:" + instrumentCurrency.toUpperCase() + accountCurrency.toUpperCase();
            fxQuote = quoteService.fetchQuote(fxSymbol, "fx", requireCurrent);
            current = current && quoteService.isCurrent(fxQuote, "fx");
            fxRate = (buying ? fxQuote.getAsk() : fxQuote.getBid()).setScale(10, RoundingMode.HALF_UP);
        }

        BigDecimal localAmount = unitPrice.multiply(quantity);
        BigDecimal settlementAmount = localAmount.multiply(fxRate).setScale(4, RoundingMode.HALF_UP);
        return new Pricing(quote, unitPrice, instrumentCurrency, fxQuote, fxRate, accountCurrency, settlementAmount, current);
    }

    public record Pricing(
            Quote quote,
            BigDecimal unitPrice,
            String instrumentCurrency,
            Quote fxQuote,
            BigDecimal fxRate,
            String settlementCurrency,
            BigDecimal settlementAmount,
            boolean current
    ) {
        /** What was priced and why, for the permanent audit trail (BR-14/15). */
        public Map<String, Object> auditDetail(String stage) {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("stage", stage);
            detail.put("symbol", quote.getSymbol());
            detail.put("bid", quote.getBid());
            detail.put("ask", quote.getAsk());
            detail.put("capturedAt", quote.getCapturedAt());
            detail.put("providerStale", quote.isProviderStale());
            detail.put("unitPrice", unitPrice);
            detail.put("instrumentCurrency", instrumentCurrency);
            if (fxQuote != null) {
                detail.put("fxSymbol", fxQuote.getSymbol());
                detail.put("fxRate", fxRate);
                detail.put("fxCapturedAt", fxQuote.getCapturedAt());
            }
            detail.put("settlementAmount", settlementAmount);
            detail.put("settlementCurrency", settlementCurrency);
            detail.put("current", current);
            detail.put("pricedAt", Instant.now());
            return detail;
        }
    }
}
