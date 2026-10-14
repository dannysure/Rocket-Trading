package com.rockettrading.rocket_trading.service;

import com.rockettrading.rocket_trading.repository.ClientAccountRepository;
import com.rockettrading.rocket_trading.repository.TransactionRepository;
import com.rockettrading.rocket_trading.repository.model.ClientAccountRecord;
import com.rockettrading.rocket_trading.repository.model.TransactionRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Opens a client's direct trading account. Cash movements are modelled in-platform (no banking
 * integration this phase), and the opening balance is a DEPOSIT ledger entry so cash is always
 * explained by the ledger.
 */
@Service
public class AccountProvisioningService {
    public static final BigDecimal DEFAULT_OPENING_CASH = new BigDecimal("10000.00");
    public static final String ACCOUNT_CURRENCY = "USD";

    private final ClientAccountRepository clientAccountRepository;
    private final TransactionRepository transactionRepository;

    public AccountProvisioningService(ClientAccountRepository clientAccountRepository,
                                      TransactionRepository transactionRepository) {
        this.clientAccountRepository = clientAccountRepository;
        this.transactionRepository = transactionRepository;
    }

    /** Creates the account if the client has none; returns true when one was created. */
    @Transactional
    public boolean ensureDirectTradingAccount(long clientId, BigDecimal openingCash) {
        if (clientAccountRepository.findDirectTradingAccountByClientId(clientId) != null) {
            return false;
        }
        BigDecimal cash = openingCash == null ? DEFAULT_OPENING_CASH : openingCash;
        ClientAccountRecord account = new ClientAccountRecord();
        account.setClientId(clientId);
        account.setAccountType("DIRECT_TRADING");
        account.setCashBalance(cash);
        account.setCurrency(ACCOUNT_CURRENCY);
        account.setOpenedDate(LocalDate.now());
        clientAccountRepository.insert(account);

        TransactionRecord deposit = new TransactionRecord();
        deposit.setAccountId(account.getAccountId());
        deposit.setTransactionType("DEPOSIT");
        deposit.setNetAmount(cash);
        deposit.setTransactionDate(Instant.now());
        transactionRepository.insert(deposit);
        return true;
    }
}
