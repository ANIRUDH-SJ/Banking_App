package com.netbanking.card.repository;

import com.netbanking.card.domain.BankCard;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BankCardRepository extends JpaRepository<BankCard, Long> {
    List<BankCard> findByCustomerIdOrderByCardIdAsc(Long customerId);
    Optional<BankCard> findByCardIdAndCustomerId(Long cardId, Long customerId);
}
