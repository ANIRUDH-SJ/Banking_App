package com.netbanking.loan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.netbanking.common.exception.ResourceNotFoundException;
import com.netbanking.discovery.CustomerDirectory;
import com.netbanking.loan.domain.Loan;
import com.netbanking.loan.domain.LoanStatus;
import com.netbanking.loan.domain.LoanType;
import com.netbanking.loan.repository.LoanRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class LoanServiceTest {

    @Mock private LoanRepository loanRepository;
    @Mock private CustomerDirectory customerService;

    private LoanService loanService;

    @BeforeEach
    void setUp() {
        loanService = new LoanService(loanRepository, customerService);
        when(customerService.requireCustomerIdForUser(7L)).thenReturn(21L);
    }

    @Test
    void returnsLoanAmountsAndDueDateForTheCustomer() {
        when(loanRepository.findByCustomerIdOrderByLoanIdAsc(21L)).thenReturn(List.of(loan()));

        var loans = loanService.getLoans(7L);

        assertThat(loans)
                .singleElement()
                .satisfies(
                        loan -> {
                            assertThat(loan.outstandingPrincipal())
                                    .isEqualByComparingTo("75000.00");
                            assertThat(loan.nextDueDate()).isEqualTo(LocalDate.of(2026, 10, 5));
                            assertThat(loan.status()).isEqualTo("ACTIVE");
                        });
    }

    @Test
    void returnsAnOwnedLoan() {
        when(loanRepository.findByLoanIdAndCustomerId(4L, 21L)).thenReturn(Optional.of(loan()));

        assertThat(loanService.getLoan(7L, 4L).loanAccountNumber()).isEqualTo("LN20260001");
    }

    @Test
    void hidesLoansOwnedByAnotherCustomer() {
        when(loanRepository.findByLoanIdAndCustomerId(4L, 21L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> loanService.getLoan(7L, 4L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private static Loan loan() {
        return new Loan(
                21L,
                "LN20260001",
                LoanType.PERSONAL,
                new BigDecimal("100000.00"),
                new BigDecimal("75000.00"),
                new BigDecimal("11.5000"),
                24,
                new BigDecimal("4684.00"),
                "INR",
                LocalDate.of(2026, 1, 5),
                LocalDate.of(2026, 10, 5),
                LocalDate.of(2027, 12, 5),
                LoanStatus.ACTIVE);
    }
}
