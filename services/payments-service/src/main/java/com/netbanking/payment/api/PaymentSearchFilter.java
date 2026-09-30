package com.netbanking.payment.api;

import java.time.LocalDate;

public record PaymentSearchFilter(
        PaymentKind kind,
        PaymentStatus status,
        LocalDate from,
        LocalDate to,
        String transactionReference) {
    public PaymentSearchFilter {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException(
                    "Payment search start date must be on or before the end date.");
        }
        transactionReference =
                transactionReference == null || transactionReference.isBlank()
                        ? null
                        : transactionReference.strip();
    }
}
