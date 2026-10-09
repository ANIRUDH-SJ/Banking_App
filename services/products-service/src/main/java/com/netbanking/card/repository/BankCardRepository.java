package com.netbanking.card.repository;

import com.netbanking.card.domain.BankCard;
import com.netbanking.card.domain.CardStatus;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BankCardRepository extends JpaRepository<BankCard, Long> {
    List<BankCard> findByCustomerIdAndCardStatusNotOrderByCardIdAsc(
            Long customerId, CardStatus excludedStatus);

    Optional<BankCard> findByCardIdAndCustomerId(Long cardId, Long customerId);
}
