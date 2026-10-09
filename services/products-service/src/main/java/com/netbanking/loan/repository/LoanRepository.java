package com.netbanking.loan.repository;

import com.netbanking.loan.domain.Loan;
import com.netbanking.loan.domain.LoanStatus;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LoanRepository extends JpaRepository<Loan, Long>, JpaSpecificationExecutor<Loan> {
    List<Loan> findByCustomerIdOrderByLoanIdAsc(Long customerId);

    Optional<Loan> findByLoanIdAndCustomerId(Long loanId, Long customerId);

    long countByLoanStatus(LoanStatus status);

    @Query(
            "select loan.currencyCode as currencyCode, sum(loan.principalAmount) as"
                    + " principalProvided, sum(loan.outstandingPrincipal) as outstandingPrincipal"
                    + " from Loan loan group by loan.currencyCode order by loan.currencyCode")
    List<LoanCurrencyTotals> summarizeAmountsByCurrency();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            "select loan from Loan loan where loan.loanId = :loanId and loan.customerId ="
                    + " :customerId")
    Optional<Loan> findByLoanIdAndCustomerIdForUpdate(
            @Param("loanId") Long loanId, @Param("customerId") Long customerId);
}
