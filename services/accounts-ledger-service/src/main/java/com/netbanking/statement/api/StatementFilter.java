package com.netbanking.statement.api;

import com.netbanking.transaction.domain.TransactionStatus;
import com.netbanking.transaction.domain.TransactionType;

import java.time.LocalDate;

public record StatementFilter(
        LocalDate from, LocalDate to, TransactionType type, TransactionStatus status) {

    public StatementFilter {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException(
                    "Statement start date must be on or before the end date.");
        }
    }
}
