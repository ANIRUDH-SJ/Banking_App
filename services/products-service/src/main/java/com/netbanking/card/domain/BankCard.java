package com.netbanking.card.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
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

    @Column(name = "pan_ciphertext", length = 256)
    private String panCiphertext;

    @Column(name = "pin_hash", length = 100)
    private String pinHash;

    @Column(name = "pin_set_at")
    private LocalDateTime pinSetAt;

    @Column(name = "pin_failed_attempts", nullable = false)
    private Integer pinFailedAttempts = 0;

    @Column(name = "pin_locked_until")
    private LocalDateTime pinLockedUntil;

    @Column(name = "credit_limit", precision = 19, scale = 4)
    private BigDecimal creditLimit;

    @Column(name = "outstanding_balance", precision = 19, scale = 4)
    private BigDecimal outstandingBalance;

    @Column(name = "statement_balance", precision = 19, scale = 4)
    private BigDecimal statementBalance;

    @Column(name = "minimum_due", precision = 19, scale = 4)
    private BigDecimal minimumDue;

    @Column(name = "statement_date")
    private LocalDate statementDate;

    @Column(name = "payment_due_date")
    private LocalDate paymentDueDate;

    protected BankCard() {}

    public BankCard(
            Long customerId,
            Long accountId,
            String cardToken,
            String lastFour,
            CardType cardType,
            CardNetwork cardNetwork,
            Integer expiryMonth,
            Integer expiryYear,
            CardStatus cardStatus) {
        if (cardToken == null || cardToken.isBlank() || cardToken.length() > 128) {
            throw new IllegalArgumentException(
                    "A card token of at most 128 characters is required.");
        }
        if (lastFour == null || !lastFour.matches("\\d{4}")) {
            throw new IllegalArgumentException(
                    "Card last four digits must contain exactly four digits.");
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

    public Long getCardId() {
        return cardId;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public Long getAccountId() {
        return accountId;
    }

    public CardType getCardType() {
        return cardType;
    }

    public CardNetwork getCardNetwork() {
        return cardNetwork;
    }

    public Integer getExpiryMonth() {
        return expiryMonth;
    }

    public Integer getExpiryYear() {
        return expiryYear;
    }

    public CardStatus getCardStatus() {
        return cardStatus;
    }

    public LocalDateTime getActivatedAt() {
        return activatedAt;
    }

    public String maskedNumber() {
        return "************" + lastFour.trim();
    }

    public String getCardToken() {
        return cardToken;
    }

    public String getLastFour() {
        return lastFour.trim();
    }

    public String getPanCiphertext() {
        return panCiphertext;
    }

    public void protectNumber(String ciphertext) {
        this.panCiphertext = ciphertext;
    }

    public boolean hasPin() {
        return pinHash != null;
    }

    public String getPinHash() {
        return pinHash;
    }

    public LocalDateTime getPinSetAt() {
        return pinSetAt;
    }

    public int getPinFailedAttempts() {
        return pinFailedAttempts == null ? 0 : pinFailedAttempts;
    }

    public LocalDateTime getPinLockedUntil() {
        return pinLockedUntil;
    }

    public boolean pinLocked(LocalDateTime now) {
        return pinLockedUntil != null && pinLockedUntil.isAfter(now);
    }

    public void changePin(String hash, LocalDateTime now) {
        this.pinHash = hash;
        this.pinSetAt = now;
        this.pinFailedAttempts = 0;
        this.pinLockedUntil = null;
    }

    public void pinVerified() {
        this.pinFailedAttempts = 0;
        this.pinLockedUntil = null;
    }

    /** Records a wrong PIN and returns the attempts left; at zero the PIN locks until {@code lockUntil}. */
    public int pinRejected(int maxAttempts, LocalDateTime lockUntil) {
        int failures = (pinLockedUntil != null ? 0 : getPinFailedAttempts()) + 1;
        if (failures >= maxAttempts) {
            pinFailedAttempts = 0;
            pinLockedUntil = lockUntil;
            return 0;
        }
        pinFailedAttempts = failures;
        pinLockedUntil = null;
        return maxAttempts - failures;
    }

    public BigDecimal getCreditLimit() {
        return creditLimit;
    }

    public BigDecimal getOutstandingBalance() {
        return outstandingBalance;
    }

    public BigDecimal getStatementBalance() {
        return statementBalance;
    }

    public BigDecimal getMinimumDue() {
        return minimumDue;
    }

    public LocalDate getStatementDate() {
        return statementDate;
    }

    public LocalDate getPaymentDueDate() {
        return paymentDueDate;
    }

    public boolean hasCreditAccount() {
        return cardType == CardType.CREDIT && creditLimit != null;
    }

    public BigDecimal availableCredit() {
        return hasCreditAccount() ? creditLimit.subtract(outstandingBalance).max(BigDecimal.ZERO) : null;
    }

    public void openCreditAccount(
            BigDecimal limit,
            BigDecimal outstanding,
            BigDecimal billed,
            LocalDate statementDate,
            LocalDate paymentDueDate) {
        if (cardType != CardType.CREDIT)
            throw new IllegalStateException("Only a credit card has a credit account.");
        if (limit == null || limit.signum() <= 0)
            throw new IllegalArgumentException("A positive credit limit is required.");
        this.creditLimit = limit;
        this.outstandingBalance = outstanding;
        this.statementDate = statementDate;
        this.paymentDueDate = paymentDueDate;
        rebill(billed);
    }

    /**
     * Moves an amount from the current bill into an EMI plan: the bill drops by the converted
     * principal and gains the first instalment; the outstanding balance gains the plan's interest.
     */
    public void convertToEmi(BigDecimal principal, BigDecimal firstInstalment, BigDecimal interest, boolean billed) {
        if (!hasCreditAccount())
            throw new IllegalStateException("This card has no credit account.");
        outstandingBalance = outstandingBalance.add(interest);
        if (billed) {
            rebill(statementBalance.subtract(principal).add(firstInstalment).max(BigDecimal.ZERO));
        }
    }

    private void rebill(BigDecimal billed) {
        this.statementBalance = billed.setScale(2, java.math.RoundingMode.HALF_UP);
        BigDecimal minimum = billed.multiply(new BigDecimal("0.05")).setScale(0, java.math.RoundingMode.CEILING);
        BigDecimal floor = new BigDecimal("200");
        this.minimumDue = billed.compareTo(floor) <= 0 ? this.statementBalance : minimum.max(floor).min(this.statementBalance);
    }

    public void apply(CardStatusAction action) {
        if (action == null) {
            throw new IllegalArgumentException("A card status action is required.");
        }
        CardStatus next =
                switch (action) {
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
