package com.netbanking.loan.repository;

import com.netbanking.loan.domain.Loan;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoanRepository extends JpaRepository<Loan, Long> {
    List<Loan> findByCustomerIdOrderByLoanIdAsc(Long customerId);
    Optional<Loan> findByLoanIdAndCustomerId(Long loanId, Long customerId);
}
