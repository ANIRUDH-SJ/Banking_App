package com.netbanking.loan.api;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record LoanPaymentResponse(
        Long loanPaymentId,
        Long loanId,
        Long sourceAccountId,
        Long transactionId,
        String transactionReference,
        BigDecimal amount,
        String currencyCode,
        BigDecimal outstandingAfter,
        String status,
        LocalDateTime paidAt) {}
