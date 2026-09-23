package com.netbanking.transaction.service;

import com.netbanking.transaction.domain.TransactionStatus;
import com.netbanking.transaction.domain.TransactionType;
import java.math.BigDecimal;

public record TransactionRecord(
        Long transactionId,
        String reference,
        TransactionType type,
        TransactionStatus status,
        BigDecimal amount,
        String currencyCode) {
}
