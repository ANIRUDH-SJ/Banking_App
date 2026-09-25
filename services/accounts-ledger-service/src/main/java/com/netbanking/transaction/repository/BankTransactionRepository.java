package com.netbanking.transaction.repository;

import com.netbanking.transaction.domain.BankTransaction;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BankTransactionRepository extends JpaRepository<BankTransaction, Long> {
    boolean existsByTransactionReference(String transactionReference);

    Optional<BankTransaction> findByTransactionReference(String transactionReference);
}
