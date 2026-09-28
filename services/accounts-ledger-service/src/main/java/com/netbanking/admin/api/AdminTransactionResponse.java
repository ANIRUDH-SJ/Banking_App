package com.netbanking.admin.api;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AdminTransactionResponse(
        Long transactionId,
        String reference,
        Long debitAccountId,
        Long creditAccountId,
        Long beneficiaryId,
        Long initiatedByUserId,
        String type,
        String status,
        BigDecimal amount,
        String currencyCode,
        String narration,
        LocalDateTime initiatedAt,
        LocalDateTime completedAt,
        String failureReason) {}
