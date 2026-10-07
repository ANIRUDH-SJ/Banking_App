package com.netbanking.card.repository;

import com.netbanking.card.domain.CardEmiPlan;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CardEmiPlanRepository extends JpaRepository<CardEmiPlan, Long> {
    List<CardEmiPlan> findByCardIdOrderByEmiPlanIdDesc(Long cardId);

    List<CardEmiPlan> findByCardTransactionIdIn(Collection<Long> cardTransactionIds);

    Optional<CardEmiPlan> findByCardTransactionId(Long cardTransactionId);

    Optional<CardEmiPlan> findByCardIdAndRequestKey(Long cardId, String requestKey);
}
