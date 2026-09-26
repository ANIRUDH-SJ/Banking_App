package com.netbanking.card.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.netbanking.card.domain.BankCard;
import com.netbanking.card.domain.CardNetwork;
import com.netbanking.card.domain.CardStatus;
import com.netbanking.card.domain.CardStatusAction;
import com.netbanking.card.domain.CardType;
import com.netbanking.card.repository.BankCardRepository;
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

    private CardService cardService;

    @BeforeEach
    void setUp() {
        cardService = new CardService(cardRepository, customerService);
        when(customerService.requireCustomerIdForUser(7L)).thenReturn(21L);
    }

    @Test
    void returnsOnlyMaskedCardDetails() {
        when(cardRepository.findByCustomerIdOrderByCardIdAsc(21L))
                .thenReturn(List.of(card(CardStatus.ACTIVE)));

        var cards = cardService.getCards(7L);

        assertThat(cards)
                .singleElement()
                .satisfies(
                        card -> {
                            assertThat(card.maskedCardNumber()).isEqualTo("************4242");
                            assertThat(card.status()).isEqualTo("ACTIVE");
                        });
    }

    @Test
    void blocksAnActiveOwnedCard() {
        BankCard card = card(CardStatus.ACTIVE);
        when(cardRepository.findByCardIdAndCustomerId(3L, 21L)).thenReturn(Optional.of(card));

        var response = cardService.changeStatus(7L, 3L, CardStatusAction.BLOCK);

        assertThat(response.status()).isEqualTo("BLOCKED");
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
