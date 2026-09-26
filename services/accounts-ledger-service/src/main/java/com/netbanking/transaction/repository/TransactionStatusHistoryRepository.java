package com.netbanking.transaction.repository;

import com.netbanking.transaction.domain.TransactionStatusHistory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TransactionStatusHistoryRepository
        extends JpaRepository<TransactionStatusHistory, Long> {
    List<TransactionStatusHistory>
            findByTransactionTransactionIdOrderByChangedAtAscStatusHistoryIdAsc(Long transactionId);
}
