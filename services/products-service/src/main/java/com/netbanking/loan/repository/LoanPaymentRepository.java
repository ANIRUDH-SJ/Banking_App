package com.netbanking.loan.repository;

import com.netbanking.loan.domain.LoanPayment;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LoanPaymentRepository extends JpaRepository<LoanPayment, Long> {
    Optional<LoanPayment> findByLoanIdAndIdempotencyKey(Long loanId, String idempotencyKey);

    Page<LoanPayment> findByLoanId(Long loanId, Pageable pageable);
}
