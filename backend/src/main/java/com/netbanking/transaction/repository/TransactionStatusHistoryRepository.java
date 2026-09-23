package com.netbanking.transaction.repository;

import com.netbanking.transaction.domain.TransactionStatusHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionStatusHistoryRepository extends JpaRepository<TransactionStatusHistory, Long> {
    List<TransactionStatusHistory> findByTransactionTransactionIdOrderByChangedAtAscStatusHistoryIdAsc(
            Long transactionId);
}
