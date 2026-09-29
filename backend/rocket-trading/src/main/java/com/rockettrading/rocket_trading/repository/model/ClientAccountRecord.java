package com.rockettrading.rocket_trading.repository.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class ClientAccountRecord {
    private Long accountId;
    private Long clientId;
    private String accountType;
    private BigDecimal cashBalance;
    private String currency;
    private LocalDate openedDate;
}
