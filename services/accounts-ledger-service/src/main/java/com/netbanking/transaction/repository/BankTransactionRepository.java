package com.netbanking.transaction.repository;

import com.netbanking.transaction.domain.BankTransaction;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface BankTransactionRepository extends JpaRepository<BankTransaction, Long> {
    boolean existsByTransactionReference(String transactionReference);

    Optional<BankTransaction> findByTransactionReference(String transactionReference);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            "select transactionEntry from BankTransaction transactionEntry where"
                    + " transactionEntry.transactionId = :id")
    Optional<BankTransaction> findByIdForUpdate(@Param("id") Long id);
}
