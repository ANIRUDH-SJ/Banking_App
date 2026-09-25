package com.netbanking.card.service;

import com.netbanking.card.api.CardResponse;
import com.netbanking.card.domain.BankCard;
import com.netbanking.card.domain.CardStatusAction;
import com.netbanking.card.repository.BankCardRepository;
import com.netbanking.common.exception.ResourceNotFoundException;
import com.netbanking.discovery.CustomerDirectory;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class CardService {

    private final BankCardRepository cardRepository;
    private final CustomerDirectory customerService;

    public CardService(BankCardRepository cardRepository, CustomerDirectory customerService) {
        this.cardRepository = cardRepository;
        this.customerService = customerService;
    }

    public List<CardResponse> getCards(Long userId) {
        Long customerId = customerService.requireCustomerIdForUser(userId);
        return cardRepository.findByCustomerIdOrderByCardIdAsc(customerId).stream()
                .map(CardService::toResponse)
                .toList();
    }

    public CardResponse getCard(Long userId, Long cardId) {
        return toResponse(findOwnedCard(userId, cardId));
    }

    @Transactional
    public CardResponse changeStatus(Long userId, Long cardId, CardStatusAction action) {
        BankCard card = findOwnedCard(userId, cardId);
        card.apply(action);
        return toResponse(card);
    }

    private BankCard findOwnedCard(Long userId, Long cardId) {
        Long customerId = customerService.requireCustomerIdForUser(userId);
        return cardRepository
                .findByCardIdAndCustomerId(cardId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Card was not found."));
    }

    private static CardResponse toResponse(BankCard card) {
        return new CardResponse(
                card.getCardId(),
                card.getAccountId(),
                card.maskedNumber(),
                card.getCardType().name(),
                card.getCardNetwork().name(),
                card.getExpiryMonth(),
                card.getExpiryYear(),
                card.getCardStatus().name(),
                card.getActivatedAt());
    }
}
