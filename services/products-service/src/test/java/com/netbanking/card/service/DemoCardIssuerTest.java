package com.netbanking.card.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.netbanking.card.domain.BankCard;
import com.netbanking.card.domain.CardNetwork;
import com.netbanking.card.domain.CardStatus;
import com.netbanking.card.domain.CardType;
import com.netbanking.card.repository.BankCardRepository;
import com.netbanking.card.repository.CardTransactionRepository;
import com.netbanking.card.security.CardNumberCipher;
import com.netbanking.discovery.LedgerClient;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

@ExtendWith(MockitoExtension.class)
class DemoCardIssuerTest {
    @Mock private BankCardRepository cards;
    @Mock private CardTransactionRepository transactions;
    @Mock private CardNumberCipher numbers;
    @Mock private LedgerClient ledger;

    @Test
    void protectsAnExistingSeedCardWithASyntheticPanThatKeepsItsLastFour() {
        when(numbers.enabled()).thenReturn(true);
        when(numbers.encrypt(anyString(), eq("seed-card-1-3"))).thenReturn("v1:encrypted");
        BankCard card = new BankCard(
                900001L, 800001L, "seed-card-1-3", "0103", CardType.CREDIT,
                CardNetwork.MASTERCARD, 9, 2031, CardStatus.ACTIVE);
        var issuer = new DemoCardIssuer(cards, transactions, numbers, ledger);

        assertThat(issuer.protectExisting(List.of(card))).containsExactly(card);

        ArgumentCaptor<String> pan = ArgumentCaptor.forClass(String.class);
        verify(numbers).encrypt(pan.capture(), eq("seed-card-1-3"));
        assertThat(pan.getValue()).matches("\\d{16}").endsWith("0103");
        assertThat(card.getPanCiphertext()).isEqualTo("v1:encrypted");
        verify(cards).saveAll(List.of(card));
    }
}
