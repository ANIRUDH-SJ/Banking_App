package com.netbanking.loan.admin.api;

import java.math.BigDecimal;

public record AdminLoanCurrencySummary(
        String currencyCode,
        BigDecimal principalProvided,
        BigDecimal outstandingPrincipal) {}
