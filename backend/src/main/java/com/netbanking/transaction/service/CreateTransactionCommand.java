package com.netbanking.transaction.service;

import com.netbanking.transaction.domain.TransactionType;
import java.math.BigDecimal;

public record CreateTransactionCommand(
        Long debitAccountId,
        Long creditAccountId,
        Long beneficiaryId,
        Long initiatedByUserId,
        TransactionType type,
        BigDecimal amount,
        String currencyCode,
        String narration) {
}
