package com.netbanking.card.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "bank_card")
public class BankCard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "card_id")
    private Long cardId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "card_token", nullable = false, unique = true, length = 128)
    private String cardToken;

    @Column(name = "last_four", nullable = false, columnDefinition = "CHAR(4)")
    private String lastFour;

    @Enumerated(EnumType.STRING)
    @Column(name = "card_type", nullable = false, length = 10)
    private CardType cardType;

    @Enumerated(EnumType.STRING)
    @Column(name = "card_network", nullable = false, length = 20)
    private CardNetwork cardNetwork;

    @Column(name = "expiry_month", nullable = false)
    private Integer expiryMonth;

    @Column(name = "expiry_year", nullable = false)
    private Integer expiryYear;

    @Enumerated(EnumType.STRING)
    @Column(name = "card_status", nullable = false, length = 20)
    private CardStatus cardStatus;

    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt;

    @Column(name = "activated_at")
    private LocalDateTime activatedAt;

    protected BankCard() {
    }

    public BankCard(Long customerId, Long accountId, String cardToken, String lastFour,
                    CardType cardType, CardNetwork cardNetwork, Integer expiryMonth,
                    Integer expiryYear, CardStatus cardStatus) {
        if (cardToken == null || cardToken.isBlank() || cardToken.length() > 128) {
            throw new IllegalArgumentException("A card token of at most 128 characters is required.");
        }
        if (lastFour == null || !lastFour.matches("\\d{4}")) {
            throw new IllegalArgumentException("Card last four digits must contain exactly four digits.");
        }
        if (expiryMonth == null || expiryMonth < 1 || expiryMonth > 12) {
            throw new IllegalArgumentException("Card expiry month must be between 1 and 12.");
        }
        this.customerId = customerId;
        this.accountId = accountId;
        this.cardToken = cardToken;
        this.lastFour = lastFour;
        this.cardType = cardType;
        this.cardNetwork = cardNetwork;
        this.expiryMonth = expiryMonth;
        this.expiryYear = expiryYear;
        this.cardStatus = cardStatus;
        this.issuedAt = LocalDateTime.now();
    }

    public Long getCardId() { return cardId; }
    public Long getCustomerId() { return customerId; }
    public Long getAccountId() { return accountId; }
    public CardType getCardType() { return cardType; }
    public CardNetwork getCardNetwork() { return cardNetwork; }
    public Integer getExpiryMonth() { return expiryMonth; }
    public Integer getExpiryYear() { return expiryYear; }
    public CardStatus getCardStatus() { return cardStatus; }
    public LocalDateTime getActivatedAt() { return activatedAt; }

    public String maskedNumber() {
        return "************" + lastFour.trim();
    }

    public void apply(CardStatusAction action) {
        if (action == null) {
            throw new IllegalArgumentException("A card status action is required.");
        }
        CardStatus next = switch (action) {
            case ACTIVATE -> cardStatus == CardStatus.INACTIVE ? CardStatus.ACTIVE : null;
            case BLOCK -> cardStatus == CardStatus.ACTIVE ? CardStatus.BLOCKED : null;
            case UNBLOCK -> cardStatus == CardStatus.BLOCKED ? CardStatus.ACTIVE : null;
        };
        if (next == null) {
            throw new IllegalStateException(
                    "Action " + action + " is not allowed while the card is " + cardStatus + ".");
        }
        cardStatus = next;
        if (action == CardStatusAction.ACTIVATE && activatedAt == null) {
            activatedAt = LocalDateTime.now();
        }
    }
}
