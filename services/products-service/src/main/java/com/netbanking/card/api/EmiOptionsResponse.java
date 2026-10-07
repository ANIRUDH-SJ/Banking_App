package com.netbanking.card.api;

import java.math.BigDecimal;
import java.util.List;

public record EmiOptionsResponse(CardTransactionResponse transaction, List<Option> options) {

    public record Option(
            int tenureMonths,
            BigDecimal annualInterestRate,
            BigDecimal monthlyInstalment,
            BigDecimal totalInterest,
            BigDecimal totalPayable) {}
}
