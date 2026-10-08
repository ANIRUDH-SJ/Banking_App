package com.netbanking.card.service;

import com.netbanking.audit.service.AuditLogService;
import com.netbanking.card.api.CardDetailsResponse;
import com.netbanking.card.api.CardResponse;
import com.netbanking.card.domain.BankCard;
import com.netbanking.card.domain.CardStatus;
import com.netbanking.card.domain.CardStatusAction;
import com.netbanking.card.repository.BankCardRepository;
import com.netbanking.card.security.CardNumberCipher;
import com.netbanking.common.exception.ConflictException;
import com.netbanking.common.exception.ResourceNotFoundException;
import com.netbanking.discovery.CustomerDirectory;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class CardService {
    static final int REVEAL_SECONDS = 30;

    private final BankCardRepository cardRepository;
    private final CustomerDirectory customerService;
    private final AuditLogService audit;
    private final CardNumberCipher numbers;
    private final Optional<DemoCardIssuer> demoCards;

    public CardService(
            BankCardRepository cardRepository,
            CustomerDirectory customerService,
            AuditLogService audit,
            CardNumberCipher numbers,
            Optional<DemoCardIssuer> demoCards) {
        this.cardRepository = cardRepository;
        this.customerService = customerService;
        this.audit = audit;
        this.numbers = numbers;
        this.demoCards = demoCards;
    }

    @Transactional
    public List<CardResponse> getCards(Long userId) {
        Long customerId = customerService.requireCustomerIdForUser(userId);
        List<BankCard> cards = cardRepository.findByCustomerIdOrderByCardIdAsc(customerId);
        if (demoCards.isPresent()) {
            cards = cards.isEmpty()
                    ? demoCards.get().issue(userId, customerId)
                    : demoCards.get().protectExisting(cards);
        }
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        return cards.stream().map(card -> toResponse(card, now)).toList();
    }

    public CardResponse getCard(Long userId, Long cardId) {
        return toResponse(findOwnedCard(userId, cardId), LocalDateTime.now(ZoneOffset.UTC));
    }

    @Transactional
    public CardResponse changeStatus(Long userId, Long cardId, CardStatusAction action) {
        BankCard card = findOwnedCard(userId, cardId);
        card.apply(action);
        audit.record(
                userId,
                "CARD_" + action.name(),
                "CARD",
                String.valueOf(cardId),
                "SUCCESS");
        return toResponse(card, LocalDateTime.now(ZoneOffset.UTC));
    }

    /** Decrypts the full number for a short on-screen reveal. Every reveal is audited. */
    @Transactional
    public CardDetailsResponse reveal(Long userId, Long cardId) {
        BankCard card = findOwnedCard(userId, cardId);
        if (card.getCardStatus() == CardStatus.CLOSED || card.getCardStatus() == CardStatus.EXPIRED)
            throw new ConflictException("Card details are not shown for a closed or expired card.");
        if (card.getPanCiphertext() == null || !numbers.enabled())
            throw new ConflictException("The full card number is not available for this card.");
        String number = numbers.decrypt(card.getPanCiphertext(), card.getCardToken());
        if (!number.endsWith(card.getLastFour()))
            throw new IllegalStateException("The stored card number does not match this card.");
        audit.record(userId, "CARD_DETAILS_REVEALED", "CARD", String.valueOf(cardId), "SUCCESS");
        return new CardDetailsResponse(
                cardId, group(number), card.getExpiryMonth(), card.getExpiryYear(), REVEAL_SECONDS);
    }

    BankCard findOwnedCard(Long userId, Long cardId) {
        Long customerId = customerService.requireCustomerIdForUser(userId);
        return cardRepository
                .findByCardIdAndCustomerId(cardId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Card was not found."));
    }

    CardResponse toResponse(BankCard card, LocalDateTime now) {
        CardResponse.Credit credit =
                card.hasCreditAccount()
                        ? new CardResponse.Credit(
                                card.getCreditLimit(),
                                card.availableCredit(),
                                card.getOutstandingBalance(),
                                card.getStatementBalance(),
                                card.getMinimumDue(),
                                card.getStatementDate(),
                                card.getPaymentDueDate())
                        : null;
        return new CardResponse(
                card.getCardId(),
                card.getAccountId(),
                card.maskedNumber(),
                card.getCardType().name(),
                card.getCardNetwork().name(),
                card.getExpiryMonth(),
                card.getExpiryYear(),
                card.getCardStatus().name(),
                card.getActivatedAt(),
                card.getLastFour(),
                card.hasPin(),
                card.pinLocked(now) ? card.getPinLockedUntil() : null,
                card.getPanCiphertext() != null && numbers.enabled(),
                credit);
    }

    private static String group(String number) {
        StringBuilder grouped = new StringBuilder();
        for (int i = 0; i < number.length(); i++) {
            if (i > 0 && i % 4 == 0) grouped.append(' ');
            grouped.append(number.charAt(i));
        }
        return grouped.toString();
    }
}
