package com.netbanking.loan.repository;

import com.netbanking.loan.domain.Loan;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LoanRepository extends JpaRepository<Loan, Long> {
    List<Loan> findByCustomerIdOrderByLoanIdAsc(Long customerId);

    Optional<Loan> findByLoanIdAndCustomerId(Long loanId, Long customerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            "select loan from Loan loan where loan.loanId = :loanId and loan.customerId ="
                    + " :customerId")
    Optional<Loan> findByLoanIdAndCustomerIdForUpdate(
            @Param("loanId") Long loanId, @Param("customerId") Long customerId);
}
