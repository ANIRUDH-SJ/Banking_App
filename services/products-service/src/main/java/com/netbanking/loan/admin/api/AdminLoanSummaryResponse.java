package com.netbanking.loan.admin.api;

import java.util.List;

public record AdminLoanSummaryResponse(
        long totalLoans,
        long activeLoans,
        List<AdminLoanCurrencySummary> currencies) {}
