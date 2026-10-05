package com.netbanking.deposit.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

class DepositPricingTest {
    private final DepositPricing pricing = new DepositPricing(new BigDecimal("6.50"),
            new BigDecimal("6.00"));

    @Test
    void fixedDepositQuoteIncludesPrincipalAndTermInterest() {
        var estimate = pricing.estimate("FD", new BigDecimal("10000"), 12,
                pricing.rate("FD"), LocalDate.of(2026, 1, 1));
        assertThat(estimate).isGreaterThan(new BigDecimal("10640"));
        assertThat(estimate).isLessThan(new BigDecimal("10660"));
    }

    @Test
    void recurringQuoteAccruesEachMonthlyContributionUntilMaturity() {
        var estimate = pricing.estimate("RD", new BigDecimal("1000"), 6,
                pricing.rate("RD"), LocalDate.of(2026, 1, 1));
        assertThat(estimate).isGreaterThan(new BigDecimal("6000"));
        assertThat(estimate).isLessThan(new BigDecimal("6200"));
    }
}
