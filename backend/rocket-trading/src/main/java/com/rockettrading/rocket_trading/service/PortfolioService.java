package com.rockettrading.rocket_trading.service;

import com.rockettrading.rocket_trading.dto.portfolio.PortfolioSummaryResponse;
import com.rockettrading.rocket_trading.dto.portfolio.PositionResponse;
import com.rockettrading.rocket_trading.exception.NotFoundException;
import com.rockettrading.rocket_trading.repository.ClientAccountRepository;
import com.rockettrading.rocket_trading.repository.HoldingRepository;
import com.rockettrading.rocket_trading.repository.model.ClientAccountRecord;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PortfolioService {
    private final ClientAccountRepository clientAccountRepository;
    private final HoldingRepository holdingRepository;

    public PortfolioService(ClientAccountRepository clientAccountRepository, HoldingRepository holdingRepository) {
        this.clientAccountRepository = clientAccountRepository;
        this.holdingRepository = holdingRepository;
    }

    public PortfolioSummaryResponse getPortfolioSummary(long clientId) {
        ClientAccountRecord account = clientAccountRepository.findDirectTradingAccountByClientId(clientId);
        if (account == null) {
            throw new NotFoundException("ACCOUNT_NOT_FOUND", "No direct trading account exists for the signed-in client");
        }

        List<PositionResponse> positions = holdingRepository.findClientHoldings(clientId)
                .stream()
                .map(PositionResponse::from)
                .toList();

        return new PortfolioSummaryResponse(
                clientId,
                account.getAccountId(),
                account.getCashBalance(),
                account.getCurrency(),
                positions
        );
    }
}
