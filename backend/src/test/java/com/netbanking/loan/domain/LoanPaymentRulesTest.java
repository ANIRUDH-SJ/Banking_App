package com.netbanking.loan.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class LoanPaymentRulesTest {

    @Test
    void appliesAPartialPaymentAndAdvancesTheDueDate() {
        Loan loan = loan(new BigDecimal("75000.00"));

        loan.applyPayment(new BigDecimal("5000.00"));

        assertThat(loan.getOutstandingPrincipal()).isEqualByComparingTo("70000.00");
        assertThat(loan.getNextDueDate()).isEqualTo(LocalDate.of(2026, 11, 5));
        assertThat(loan.getLoanStatus()).isEqualTo(LoanStatus.ACTIVE);
    }

    @Test
    void marksTheLoanPaidOffWhenTheOutstandingAmountIsCleared() {
        Loan loan = loan(new BigDecimal("75000.00"));

        loan.applyPayment(new BigDecimal("75000.00"));

        assertThat(loan.getOutstandingPrincipal()).isZero();
        assertThat(loan.getNextDueDate()).isNull();
        assertThat(loan.getLoanStatus()).isEqualTo(LoanStatus.PAID_OFF);
    }

    @Test
    void keepsTheDueDateForAPaymentBelowOneInstallment() {
        Loan loan = loan(new BigDecimal("75000.00"));

        loan.applyPayment(new BigDecimal("1000.00"));

        assertThat(loan.getNextDueDate()).isEqualTo(LocalDate.of(2026, 10, 5));
    }

    @Test
    void rejectsAnOverpayment() {
        Loan loan = loan(new BigDecimal("75000.00"));

        assertThatThrownBy(() -> loan.applyPayment(new BigDecimal("75000.01")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("outstanding");
    }

    private static Loan loan(BigDecimal outstanding) {
        return new Loan(21L, "LN20260001", LoanType.PERSONAL,
                new BigDecimal("100000.00"), outstanding, new BigDecimal("11.5000"),
                24, new BigDecimal("4684.00"), "INR", LocalDate.of(2026, 1, 5),
                LocalDate.of(2026, 10, 5), LocalDate.of(2027, 12, 5), LoanStatus.ACTIVE);
    }
}
