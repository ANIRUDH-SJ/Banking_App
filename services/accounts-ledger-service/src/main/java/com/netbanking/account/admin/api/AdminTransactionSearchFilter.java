package com.netbanking.account.admin.api;

import com.netbanking.transaction.domain.TransactionStatus;
import com.netbanking.transaction.domain.TransactionType;

import java.time.LocalDate;

public record AdminTransactionSearchFilter(
        String reference,
        Long accountId,
        Long initiatedByUserId,
        TransactionType type,
        TransactionStatus status,
        LocalDate from,
        LocalDate to) {
    public AdminTransactionSearchFilter {
        reference = reference == null || reference.isBlank() ? null : reference.strip();
        if (accountId != null && accountId <= 0) {
            throw new IllegalArgumentException("Account identifier must be positive.");
        }
        if (initiatedByUserId != null && initiatedByUserId <= 0) {
            throw new IllegalArgumentException("User identifier must be positive.");
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException(
                    "Transaction search start date must be on or before the end date.");
        }
    }
}
