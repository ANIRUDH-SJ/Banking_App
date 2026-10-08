package com.netbanking.card.service;

import com.netbanking.audit.service.AuditLogService;
import com.netbanking.card.api.CardResponse;
import com.netbanking.card.api.SetCardPinRequest;
import com.netbanking.card.domain.BankCard;
import com.netbanking.card.domain.CardStatus;
import com.netbanking.card.domain.CardType;
import com.netbanking.card.security.PinTransportKeys;
import com.netbanking.common.exception.ConflictException;
import com.netbanking.common.exception.VerificationFailedException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Set;

/**
 * Sets, changes and verifies card PINs. PINs arrive encrypted (see {@link PinTransportKeys}) and
 * are kept only as BCrypt hashes. Wrong attempts are counted per card and lock the PIN; the
 * counter is committed even though the request fails.
 */
@Service
public class CardPinService {
    private static final Set<String> WEAK =
            Set.of("0123", "1234", "2345", "3456", "4567", "5678", "6789",
                    "9876", "8765", "7654", "6543", "5432", "4321", "3210", "1212", "1004", "2580");

    private final CardService cards;
    private final PinTransportKeys keys;
    private final AuditLogService audit;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);
    private final int maxAttempts;
    private final Duration lockDuration;
    private final Clock clock;

    @Autowired
    public CardPinService(
            CardService cards,
            PinTransportKeys keys,
            AuditLogService audit,
            @Value("${app.cards.pin.max-attempts:3}") int maxAttempts,
            @Value("${app.cards.pin.lock-minutes:30}") long lockMinutes) {
        this(cards, keys, audit, maxAttempts, Duration.ofMinutes(lockMinutes), Clock.systemUTC());
    }

    CardPinService(
            CardService cards,
            PinTransportKeys keys,
            AuditLogService audit,
            int maxAttempts,
            Duration lockDuration,
            Clock clock) {
        if (maxAttempts < 1 || maxAttempts > 10)
            throw new IllegalArgumentException("PIN attempts must be between 1 and 10.");
        this.cards = cards;
        this.keys = keys;
        this.audit = audit;
        this.maxAttempts = maxAttempts;
        this.lockDuration = lockDuration;
        this.clock = clock;
    }

    @Transactional(noRollbackFor = VerificationFailedException.class)
    public CardResponse setPin(Long userId, Long cardId, SetCardPinRequest request) {
        BankCard card = cards.findOwnedCard(userId, cardId);
        if (card.getCardStatus() != CardStatus.ACTIVE && card.getCardStatus() != CardStatus.INACTIVE)
            throw new ConflictException(
                    "A PIN cannot be set while the card is " + card.getCardStatus().name().toLowerCase() + ".");
        boolean changing = card.hasPin();
        if (changing) {
            if (request.encryptedCurrentPin() == null || request.encryptedCurrentPin().isBlank())
                throw new VerificationFailedException(
                        "PIN_REQUIRED", "currentPin", "Enter the current PIN to change it.");
            check(userId, card, keys.open(request.pinKeyId(), request.encryptedCurrentPin(), "currentPin"),
                    "currentPin", "PIN_CHANGE");
        }
        String pin = keys.open(request.pinKeyId(), request.encryptedPin(), "newPin");
        requireStrong(pin);
        if (changing && encoder.matches(material(card, pin), card.getPinHash()))
            throw new VerificationFailedException(
                    "PIN_REUSED", "newPin", "Choose a PIN that is different from the current one.");
        card.changePin(encoder.encode(material(card, pin)), now());
        audit.record(userId, changing ? "CARD_PIN_CHANGED" : "CARD_PIN_SET", "CARD",
                String.valueOf(cardId), "SUCCESS");
        return cards.toResponse(card, now());
    }

    /**
     * Authorizes a payment from {@code accountId} with the PIN of the active debit card linked to
     * that account.
     */
    @Transactional(noRollbackFor = VerificationFailedException.class)
    public void verifyForPayment(
            Long userId, Long cardId, Long accountId, String keyId, String encryptedPin, String purpose) {
        BankCard card = cards.findOwnedCard(userId, cardId);
        if (card.getCardType() != CardType.DEBIT || !card.getAccountId().equals(accountId))
            throw new ConflictException("Use the PIN of the debit card linked to the paying account.");
        if (card.getCardStatus() != CardStatus.ACTIVE)
            throw new ConflictException("The debit card must be active to authorize with its PIN.");
        check(userId, card, keys.open(keyId, encryptedPin, "pin"), "pin", purpose);
        audit.record(userId, "CARD_PIN_VERIFIED", "CARD", String.valueOf(cardId), "SUCCESS",
                "purpose=" + purpose);
    }

    private void check(Long userId, BankCard card, String pin, String field, String purpose) {
        LocalDateTime now = now();
        if (!card.hasPin())
            throw new VerificationFailedException("PIN_NOT_SET", field, "Set a PIN for this card first.");
        if (card.pinLocked(now))
            throw locked(card, now, field);
        if (encoder.matches(material(card, pin), card.getPinHash())) {
            card.pinVerified();
            return;
        }
        int left = card.pinRejected(maxAttempts, now.plus(lockDuration));
        audit.record(userId, "CARD_PIN_REJECTED", "CARD", String.valueOf(card.getCardId()), "DENIED",
                "purpose=" + purpose);
        if (left == 0) {
            audit.record(userId, "CARD_PIN_LOCKED", "CARD", String.valueOf(card.getCardId()), "DENIED");
            throw locked(card, now, field);
        }
        throw new VerificationFailedException(
                "PIN_INCORRECT",
                field,
                "Incorrect PIN. " + left + (left == 1 ? " attempt" : " attempts")
                        + " left before the PIN is locked.");
    }

    private static VerificationFailedException locked(BankCard card, LocalDateTime now, String field) {
        long seconds = Math.max(1, Duration.between(now, card.getPinLockedUntil()).toSeconds());
        long minutes = Math.max(1, (seconds + 59) / 60);
        return new VerificationFailedException(
                "PIN_LOCKED",
                field,
                "Too many incorrect PIN attempts. The PIN is locked; try again in " + minutes
                        + (minutes == 1 ? " minute" : " minutes") + " or use a one-time code.",
                seconds);
    }

    private static void requireStrong(String pin) {
        if (pin.chars().distinct().count() == 1 || WEAK.contains(pin))
            throw new VerificationFailedException(
                    "PIN_WEAK", "newPin", "Avoid repeated or sequential digits such as 1111 or 1234.");
    }

    /** The PIN comes first so BCrypt's 72-byte input limit can never cut it off. */
    private static String material(BankCard card, String pin) {
        try {
            return pin + ":" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(card.getCardToken().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
