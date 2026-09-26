package com.netbanking.statement.api;

import java.time.LocalDate;

public record StatementFilter(
        LocalDate from,
        LocalDate to,
        StatementTransactionType type,
        StatementTransactionStatus status) {

    public StatementFilter {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException(
                    "Statement start date must be on or before the end date.");
        }
    }
}
