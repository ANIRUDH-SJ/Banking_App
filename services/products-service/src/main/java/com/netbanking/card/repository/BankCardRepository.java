package com.netbanking.card.repository;

import com.netbanking.card.domain.BankCard;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BankCardRepository extends JpaRepository<BankCard, Long> {
    List<BankCard> findByCustomerIdOrderByCardIdAsc(Long customerId);

    Optional<BankCard> findByCardIdAndCustomerId(Long cardId, Long customerId);
}
