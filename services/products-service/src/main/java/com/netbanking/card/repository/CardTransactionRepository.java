package com.netbanking.card.repository;

import com.netbanking.card.domain.CardTransaction;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CardTransactionRepository extends JpaRepository<CardTransaction, Long> {
    Page<CardTransaction> findByCardId(Long cardId, Pageable pageable);

    Optional<CardTransaction> findByCardTransactionIdAndCardId(Long cardTransactionId, Long cardId);
}
