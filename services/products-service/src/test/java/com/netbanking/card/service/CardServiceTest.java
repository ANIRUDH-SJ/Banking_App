package com.netbanking.card.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.netbanking.audit.service.AuditLogService;
import com.netbanking.card.domain.BankCard;
import com.netbanking.card.domain.CardNetwork;
import com.netbanking.card.domain.CardStatus;
import com.netbanking.card.domain.CardStatusAction;
import com.netbanking.card.domain.CardType;
import com.netbanking.card.repository.BankCardRepository;
import com.netbanking.card.security.CardNumberCipher;
import com.netbanking.common.exception.ResourceNotFoundException;
import com.netbanking.discovery.CustomerDirectory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class CardServiceTest {

    @Mock private BankCardRepository cardRepository;
    @Mock private CustomerDirectory customerService;
    @Mock private AuditLogService audit;

    private CardService cardService;

    @BeforeEach
    void setUp() {
        cardService =
                new CardService(
                        cardRepository,
                        customerService,
                        audit,
                        new CardNumberCipher(""),
                        java.util.Optional.empty());
        when(customerService.requireCustomerIdForUser(7L)).thenReturn(21L);
    }

    @Test
    void returnsOnlyMaskedCardDetails() {
        when(cardRepository.findByCustomerIdAndCardStatusNotOrderByCardIdAsc(
                        21L, CardStatus.CLOSED))
                .thenReturn(List.of(card(CardStatus.ACTIVE)));

        var cards = cardService.getCards(7L);

        assertThat(cards)
                .singleElement()
                .satisfies(
                        card -> {
                            assertThat(card.maskedCardNumber()).isEqualTo("************4242");
                            assertThat(card.status()).isEqualTo("ACTIVE");
                            assertThat(card.revealable()).isFalse();
                        });
        verify(cardRepository)
                .findByCustomerIdAndCardStatusNotOrderByCardIdAsc(21L, CardStatus.CLOSED);
    }

    @Test
    void doesNotOfferRevealWhenCardIsEncryptedButKeyIsMissing() {
        BankCard card = card(CardStatus.ACTIVE);
        card.protectNumber("v1:encrypted");
        when(cardRepository.findByCustomerIdAndCardStatusNotOrderByCardIdAsc(
                        21L, CardStatus.CLOSED))
                .thenReturn(List.of(card));

        assertThat(cardService.getCards(7L)).singleElement()
                .satisfies(result -> assertThat(result.revealable()).isFalse());
    }

    @Test
    void blocksAnActiveOwnedCard() {
        BankCard card = card(CardStatus.ACTIVE);
        when(cardRepository.findByCardIdAndCustomerId(3L, 21L)).thenReturn(Optional.of(card));

        var response = cardService.changeStatus(7L, 3L, CardStatusAction.BLOCK);

        assertThat(response.status()).isEqualTo("BLOCKED");
        verify(audit).record(7L, "CARD_BLOCK", "CARD", "3", "SUCCESS");
    }

    @Test
    void rejectsAnInvalidStatusTransition() {
        when(cardRepository.findByCardIdAndCustomerId(3L, 21L))
                .thenReturn(Optional.of(card(CardStatus.INACTIVE)));

        assertThatThrownBy(() -> cardService.changeStatus(7L, 3L, CardStatusAction.UNBLOCK))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not allowed");
    }

    @Test
    void hidesCardsOwnedByAnotherCustomer() {
        when(cardRepository.findByCardIdAndCustomerId(3L, 21L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cardService.getCard(7L, 3L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private static BankCard card(CardStatus status) {
        return new BankCard(
                21L,
                8L,
                "tok_demo_4242",
                "4242",
                CardType.DEBIT,
                CardNetwork.VISA,
                12,
                2030,
                status);
    }
}
