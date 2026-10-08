package com.netbanking.card.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.netbanking.audit.service.AuditLogService;
import com.netbanking.card.api.CardResponse;
import com.netbanking.card.api.SetCardPinRequest;
import com.netbanking.card.domain.BankCard;
import com.netbanking.card.domain.CardNetwork;
import com.netbanking.card.domain.CardStatus;
import com.netbanking.card.domain.CardType;
import com.netbanking.card.security.PinTransportKeys;
import com.netbanking.card.security.PinTransportKeysTest;
import com.netbanking.common.exception.ConflictException;
import com.netbanking.common.exception.VerificationFailedException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

class CardPinServiceTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-07T10:00:00Z"), ZoneOffset.UTC);
    private final CardService cards = mock(CardService.class);
    private final AuditLogService audit = mock(AuditLogService.class);
    private PinTransportKeys keys;
    private CardPinService pins;
    private BankCard debit;

    @BeforeEach
    void setUp() {
        keys = PinTransportKeysTest.keys(clock);
        pins = new CardPinService(cards, keys, audit, 3, Duration.ofMinutes(30), clock);
        debit = card(CardType.DEBIT, 10L);
        when(cards.findOwnedCard(7L, 1L)).thenReturn(debit);
    }

    @Test
    void setsAPinAndStoresOnlyAHash() {
        CardResponse cardResponse = mock(CardResponse.class);
        when(cardResponse.pinSet()).thenReturn(true);
        when(cards.toResponse(eq(debit), any())).thenReturn(cardResponse);
        var response = pins.setPin(7L, 1L, request("4826", null));

        assertThat(response.pinSet()).isTrue();
        assertThat(debit.getPinHash()).startsWith("$2").doesNotContain("4826");
        verify(audit).record(7L, "CARD_PIN_SET", "CARD", "1", "SUCCESS");
    }

    @Test
    void refusesRepeatedAndSequentialPins() {
        assertThatThrownBy(() -> pins.setPin(7L, 1L, request("1111", null)))
                .isInstanceOfSatisfying(VerificationFailedException.class,
                        e -> assertThat(e.code()).isEqualTo("PIN_WEAK"));
        assertThatThrownBy(() -> pins.setPin(7L, 1L, request("1234", null)))
                .isInstanceOf(VerificationFailedException.class);
        assertThat(debit.hasPin()).isFalse();
    }

    @Test
    void changingAPinNeedsTheCurrentOne() {
        pins.setPin(7L, 1L, request("4826", null));

        assertThatThrownBy(() -> pins.setPin(7L, 1L, request("7391", null)))
                .isInstanceOfSatisfying(VerificationFailedException.class,
                        e -> assertThat(e.code()).isEqualTo("PIN_REQUIRED"));
        assertThatThrownBy(() -> pins.setPin(7L, 1L, request("7391", "0000")))
                .isInstanceOfSatisfying(VerificationFailedException.class,
                        e -> assertThat(e.code()).isEqualTo("PIN_INCORRECT"));

        pins.setPin(7L, 1L, request("7391", "4826"));
        pins.verifyForPayment(7L, 1L, 10L, keys.keyId(), PinTransportKeysTest.seal(keys, "7391", clock), "FUND_TRANSFER");
        verify(audit).record(7L, "CARD_PIN_CHANGED", "CARD", "1", "SUCCESS");
    }

    @Test
    void locksThePinAfterThreeWrongAttemptsAndCountsDown() {
        pins.setPin(7L, 1L, request("4826", null));

        assertThatThrownBy(() -> pay("0000"))
                .hasMessageContaining("2 attempts left");
        assertThatThrownBy(() -> pay("0000"))
                .hasMessageContaining("1 attempt left");
        assertThatThrownBy(() -> pay("0000"))
                .isInstanceOfSatisfying(VerificationFailedException.class, e -> {
                    assertThat(e.code()).isEqualTo("PIN_LOCKED");
                    assertThat(e.locked()).isTrue();
                    assertThat(e.retryAfterSeconds()).isEqualTo(1800);
                });
        assertThatThrownBy(() -> pay("4826"))
                .isInstanceOfSatisfying(VerificationFailedException.class,
                        e -> assertThat(e.code()).isEqualTo("PIN_LOCKED"));
        verify(audit).record(7L, "CARD_PIN_LOCKED", "CARD", "1", "DENIED");
    }

    @Test
    void aCorrectPinResetsTheFailureCount() {
        pins.setPin(7L, 1L, request("4826", null));
        assertThatThrownBy(() -> pay("0000")).isInstanceOf(VerificationFailedException.class);

        pay("4826");

        assertThat(debit.getPinFailedAttempts()).isZero();
        verify(audit).record(eq(7L), eq("CARD_PIN_VERIFIED"), eq("CARD"), eq("1"), eq("SUCCESS"), any());
    }

    @Test
    void onlyTheDebitCardOfThePayingAccountCanAuthorize() {
        pins.setPin(7L, 1L, request("4826", null));

        assertThatThrownBy(() -> pins.verifyForPayment(7L, 1L, 99L, keys.keyId(),
                PinTransportKeysTest.seal(keys, "4826", clock), "FUND_TRANSFER"))
                .isInstanceOf(ConflictException.class);

        BankCard credit = card(CardType.CREDIT, 10L);
        when(cards.findOwnedCard(7L, 2L)).thenReturn(credit);
        assertThatThrownBy(() -> pins.verifyForPayment(7L, 2L, 10L, keys.keyId(),
                PinTransportKeysTest.seal(keys, "4826", clock), "FUND_TRANSFER"))
                .isInstanceOf(ConflictException.class);
    }

    private void pay(String pin) {
        pins.verifyForPayment(7L, 1L, 10L, keys.keyId(), PinTransportKeysTest.seal(keys, pin, clock), "FUND_TRANSFER");
    }

    private SetCardPinRequest request(String pin, String current) {
        return new SetCardPinRequest(
                keys.keyId(),
                PinTransportKeysTest.seal(keys, pin, clock),
                current == null ? null : PinTransportKeysTest.seal(keys, current, clock));
    }

    private static BankCard card(CardType type, Long accountId) {
        BankCard card = new BankCard(21L, accountId, "token-" + type, "4242", type, CardNetwork.VISA, 12, 2030,
                CardStatus.ACTIVE);
        ReflectionTestUtils.setField(card, "cardId", type == CardType.DEBIT ? 1L : 2L);
        return card;
    }
}
