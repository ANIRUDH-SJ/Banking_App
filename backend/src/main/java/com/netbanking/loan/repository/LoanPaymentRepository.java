package com.netbanking.loan.repository;

import com.netbanking.loan.domain.LoanPayment;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoanPaymentRepository extends JpaRepository<LoanPayment, Long> {
    Optional<LoanPayment> findByLoanIdAndIdempotencyKey(Long loanId, String idempotencyKey);
    Page<LoanPayment> findByLoanId(Long loanId, Pageable pageable);
}
