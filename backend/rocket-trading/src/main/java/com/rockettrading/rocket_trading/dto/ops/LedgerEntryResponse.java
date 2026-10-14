package com.rockettrading.rocket_trading.dto.ops;

import com.rockettrading.rocket_trading.repository.model.TransactionRecord;

import java.math.BigDecimal;
import java.time.Instant;

public record LedgerEntryResponse(long transactionId, String type, BigDecimal quantity, BigDecimal unitPrice,
                                  BigDecimal netAmount, Instant recordedAt) {
    public static LedgerEntryResponse from(TransactionRecord record) {
        return new LedgerEntryResponse(record.getTransactionId(), record.getTransactionType(), record.getQuantity(),
                record.getUnitPrice(), record.getNetAmount(), record.getTransactionDate());
    }
}
