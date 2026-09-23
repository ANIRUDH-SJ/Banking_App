package com.netbanking.loan.api;

import java.math.BigDecimal;
import java.time.LocalDate;

public record LoanResponse(
        Long loanId,
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
        String status) {
}
