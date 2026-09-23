package com.netbanking.transaction.api;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransactionResponse(
        Long entryId,
        Long transactionId,
        String reference,
        String type,
        String status,
        String entryType,
        BigDecimal amount,
        String currencyCode,
        BigDecimal balanceAfter,
        String narration,
        LocalDateTime postedAt) {
}
