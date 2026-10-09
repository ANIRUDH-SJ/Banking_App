package com.netbanking.loan.admin.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.netbanking.loan.domain.Loan;
import com.netbanking.loan.domain.LoanStatus;
import com.netbanking.loan.domain.LoanType;
import com.netbanking.loan.repository.LoanCurrencyTotals;
import com.netbanking.loan.repository.LoanRepository;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

class AdminLoanServiceTest {
    private final LoanRepository loans = mock(LoanRepository.class);
    private final AdminLoanService service = new AdminLoanService(loans);

    @Test
    void exposesCustomerAndLoanAmountsToAdministrators() {
        when(loans.findAll(
                        ArgumentMatchers.<Specification<Loan>>any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(loan("INR", "100000", "75000"))));

        var result = service.search("LN2026", 21L, LoanStatus.ACTIVE, 0, 20);

        assertThat(result)
                .singleElement()
                .satisfies(
                        row -> {
                            assertThat(row.customerId()).isEqualTo(21L);
                            assertThat(row.principalAmount()).isEqualByComparingTo("100000");
                            assertThat(row.outstandingPrincipal()).isEqualByComparingTo("75000");
                            assertThat(row.status()).isEqualTo("ACTIVE");
                        });
    }

    @Test
    void keepsLoanExposureSeparatedByCurrency() {
        LoanCurrencyTotals inr = totals("INR", "150000", "115000");
        LoanCurrencyTotals usd = totals("USD", "1000", "900");
        when(loans.count()).thenReturn(3L);
        when(loans.countByLoanStatus(LoanStatus.ACTIVE)).thenReturn(3L);
        when(loans.summarizeAmountsByCurrency()).thenReturn(List.of(inr, usd));

        var summary = service.summary();

        assertThat(summary.totalLoans()).isEqualTo(3);
        assertThat(summary.activeLoans()).isEqualTo(3);
        assertThat(summary.currencies())
                .extracting(
                        currency -> currency.currencyCode(),
                        currency -> currency.principalProvided(),
                        currency -> currency.outstandingPrincipal())
                .containsExactly(
                        tuple("INR", new BigDecimal("150000"), new BigDecimal("115000")),
                        tuple("USD", new BigDecimal("1000"), new BigDecimal("900")));
    }

    @Test
    void validatesPagingBeforeQueryingTheDatabase() {
        assertThatThrownBy(() -> service.search(null, null, null, -1, 20))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(loans);
    }

    private static Loan loan(String currency, String principal, String outstanding) {
        return new Loan(
                21L,
                "LN20260001",
                LoanType.PERSONAL,
                new BigDecimal(principal),
                new BigDecimal(outstanding),
                new BigDecimal("11.5000"),
                24,
                new BigDecimal("4684.00"),
                currency,
                LocalDate.of(2026, 1, 5),
                LocalDate.of(2026, 10, 5),
                LocalDate.of(2027, 12, 5),
                LoanStatus.ACTIVE);
    }

    private static LoanCurrencyTotals totals(
            String currency, String principal, String outstanding) {
        LoanCurrencyTotals totals = mock(LoanCurrencyTotals.class);
        when(totals.getCurrencyCode()).thenReturn(currency);
        when(totals.getPrincipalProvided()).thenReturn(new BigDecimal(principal));
        when(totals.getOutstandingPrincipal()).thenReturn(new BigDecimal(outstanding));
        return totals;
    }
}
