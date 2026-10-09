package com.netbanking.loan.repository;

import java.math.BigDecimal;

public interface LoanCurrencyTotals {
    String getCurrencyCode();

    BigDecimal getPrincipalProvided();

    BigDecimal getOutstandingPrincipal();
}
