package com.netbanking.transaction.repository;

import com.netbanking.transaction.domain.BankTransaction;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BankTransactionRepository extends JpaRepository<BankTransaction, Long> {
    boolean existsByTransactionReference(String transactionReference);
    Optional<BankTransaction> findByTransactionReference(String transactionReference);
}
