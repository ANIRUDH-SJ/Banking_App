package com.netbanking.loan.admin.api;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AdminLoanResponse(
        Long loanId,
        Long customerId,
        String loanAccountNumber,
        String loanType,
        BigDecimal principalAmount,
        BigDecimal outstandingPrincipal,
        BigDecimal interestRate,
        Integer termMonths,
        BigDecimal emiAmount,
        String currencyCode,
        LocalDate disbursedOn,
        LocalDate nextDueDate,
        LocalDate maturityDate,
        String status) {}
