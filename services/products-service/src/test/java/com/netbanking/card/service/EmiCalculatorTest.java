package com.netbanking.card.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

class EmiCalculatorTest {
    private final EmiCalculator calculator = new EmiCalculator(EmiCalculator.DEFAULT_RATES);

    @Test
    void offersThreeSixAndNineMonthsWithReducingBalanceInstalments() {
        var options = calculator.options(new BigDecimal("38990.00"));

        assertThat(options).extracting(EmiCalculator.Option::tenureMonths).containsExactly(3, 6, 9);
        var three = options.get(0);
        assertThat(three.annualInterestRate()).isEqualByComparingTo("13.00");
        assertThat(three.monthlyInstalment()).isEqualByComparingTo("13279.27");
        assertThat(three.totalPayable()).isEqualByComparingTo("39837.81");
        assertThat(three.totalInterest()).isEqualByComparingTo("847.81");
        assertThat(options.get(2).totalInterest()).isGreaterThan(options.get(1).totalInterest());
    }

    @Test
    void rejectsUnsupportedTenures() {
        assertThatThrownBy(() -> calculator.option(new BigDecimal("5000"), 12))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EmiCalculator("12:10"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
