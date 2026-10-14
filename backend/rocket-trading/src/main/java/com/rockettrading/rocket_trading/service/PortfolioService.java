package com.rockettrading.rocket_trading.service;

import com.rockettrading.rocket_trading.dto.portfolio.PortfolioSummaryResponse;
import com.rockettrading.rocket_trading.dto.portfolio.PortfolioValuationResponse;
import com.rockettrading.rocket_trading.dto.portfolio.PortfolioValuationResponse.PositionValuation;
import com.rockettrading.rocket_trading.dto.portfolio.PositionResponse;
import com.rockettrading.rocket_trading.exception.NotFoundException;
import com.rockettrading.rocket_trading.model.Quote;
import com.rockettrading.rocket_trading.repository.ClientAccountRepository;
import com.rockettrading.rocket_trading.repository.HoldingRepository;
import com.rockettrading.rocket_trading.repository.TransactionRepository;
import com.rockettrading.rocket_trading.repository.model.ClientAccountRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Service
public class PortfolioService {
    private final ClientAccountRepository clientAccountRepository;
    private final HoldingRepository holdingRepository;
    private final TransactionRepository transactionRepository;
    private final QuoteService quoteService;
    private final TransactionTemplate snapshot;

    public PortfolioService(ClientAccountRepository clientAccountRepository,
                            HoldingRepository holdingRepository,
                            TransactionRepository transactionRepository,
                            QuoteService quoteService,
                            PlatformTransactionManager transactionManager) {
        this.clientAccountRepository = clientAccountRepository;
        this.holdingRepository = holdingRepository;
        this.transactionRepository = transactionRepository;
        this.quoteService = quoteService;
        // One consistent snapshot: cash and holdings always reflect the same set of settled trades
        this.snapshot = new TransactionTemplate(transactionManager);
        this.snapshot.setReadOnly(true);
        this.snapshot.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }

    public PortfolioSummaryResponse getPortfolioSummary(long clientId) {
        return snapshot.execute(status -> readBook(clientId));
    }

    private PortfolioSummaryResponse readBook(long clientId) {
        ClientAccountRecord account = requireAccount(clientId);
        List<PositionResponse> positions = holdingRepository.findOpenAccountHoldings(account.getAccountId())
                .stream()
                .map(PositionResponse::from)
                .toList();

        return new PortfolioSummaryResponse(
                clientId,
                account.getAccountId(),
                account.getCashBalance(),
                account.getCurrency(),
                transactionRepository.sumNetDeposits(account.getAccountId()),
                positions
        );
    }

    public PortfolioValuationResponse getValuation(long clientId) {
        PortfolioSummaryResponse book = getPortfolioSummary(clientId);
        String currency = book.currency();

        // Each position is priced independently; one market being unavailable never hides the rest
        List<PositionValuation> positions = book.positions().parallelStream()
                .map(position -> value(position, currency))
                .toList();

        BigDecimal holdingsValue = positions.stream()
                .map(PositionValuation::marketValue).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal dayChange = positions.stream()
                .filter(p -> p.marketValue() != null && p.changePercent() != null)
                .map(p -> dayChangeOf(p.marketValue(), p.changePercent()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalValue = book.cashBalance().add(holdingsValue);

        return new PortfolioValuationResponse(
                currency,
                book.cashBalance(),
                holdingsValue.setScale(2, RoundingMode.HALF_UP),
                totalValue.setScale(2, RoundingMode.HALF_UP),
                book.netDeposits(),
                totalValue.subtract(book.netDeposits()).setScale(2, RoundingMode.HALF_UP),
                dayChange.setScale(2, RoundingMode.HALF_UP),
                positions.stream().allMatch(PositionValuation::priced),
                Instant.now(),
                positions
        );
    }

    private PositionValuation value(PositionResponse position, String accountCurrency) {
        try {
            String market = QuoteService.marketFor(position.symbol());
            Quote quote = quoteService.displayQuote(position.symbol(), market);
            BigDecimal fxRate = BigDecimal.ONE;
            if (position.instrumentCurrency() != null && !position.instrumentCurrency().equalsIgnoreCase(accountCurrency)) {
                fxRate = quoteService.displayQuote("FX:" + position.instrumentCurrency() + accountCurrency, "fx").getPrice();
            }
            // Marked at the bid: what the client would receive if they sold now
            BigDecimal marketValue = quote.getBid().multiply(position.quantity()).multiply(fxRate).setScale(2, RoundingMode.HALF_UP);
            BigDecimal unrealized = position.averageCost() == null ? null
                    : marketValue.subtract(position.averageCost().multiply(position.quantity())).setScale(2, RoundingMode.HALF_UP);
            return new PositionValuation(position.symbol(), position.name(), position.assetClass(), position.segment(),
                    position.quantity(), position.averageCost(), position.instrumentCurrency(), quote.getPrice(), fxRate,
                    marketValue, unrealized, quote.getChangePercent(), quote.getCapturedAt(), true);
        } catch (RuntimeException unavailable) {
            return new PositionValuation(position.symbol(), position.name(), position.assetClass(), position.segment(),
                    position.quantity(), position.averageCost(), position.instrumentCurrency(), null, null,
                    null, null, null, null, false);
        }
    }

    // Value today minus the value at the previous close: v - v / (1 + pct/100)
    private BigDecimal dayChangeOf(BigDecimal value, BigDecimal changePercent) {
        BigDecimal factor = BigDecimal.ONE.add(changePercent.divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP));
        if (factor.signum() <= 0) return BigDecimal.ZERO;
        return value.subtract(value.divide(factor, 10, RoundingMode.HALF_UP));
    }

    private ClientAccountRecord requireAccount(long clientId) {
        ClientAccountRecord account = clientAccountRepository.findDirectTradingAccountByClientId(clientId);
        if (account == null) {
            throw new NotFoundException("ACCOUNT_NOT_FOUND", "No direct trading account exists for the signed-in client");
        }
        return account;
    }
}
