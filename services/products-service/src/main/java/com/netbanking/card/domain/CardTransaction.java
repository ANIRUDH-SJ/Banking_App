package com.netbanking.card.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** A purchase, refund or payment posted to a credit card. */
@Entity
@Table(name = "card_transaction")
public class CardTransaction {
    public static final String PURCHASE = "PURCHASE";
    public static final String REFUND = "REFUND";
    public static final String PAYMENT = "PAYMENT";
    public static final String POSTED = "POSTED";
    public static final String PENDING = "PENDING";
    public static final String CONVERTED_TO_EMI = "CONVERTED_TO_EMI";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "card_transaction_id")
    private Long cardTransactionId;

    @Column(name = "card_id", nullable = false)
    private Long cardId;

    @Column(name = "transaction_reference", nullable = false, unique = true, length = 30)
    private String reference;

    @Column(name = "merchant_name", nullable = false, length = 100)
    private String merchantName;

    @Column(name = "merchant_category", nullable = false, length = 40)
    private String merchantCategory;

    @Column(name = "transaction_type", nullable = false, length = 20)
    private String transactionType;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "transaction_status", nullable = false, length = 20)
    private String status;

    @Column(name = "posted_at", nullable = false)
    private LocalDateTime postedAt;

    @Column(name = "billed_on")
    private LocalDate billedOn;

    protected CardTransaction() {}

    public CardTransaction(
            Long cardId,
            String reference,
            String merchantName,
            String merchantCategory,
            String transactionType,
            BigDecimal amount,
            String status,
            LocalDateTime postedAt,
            LocalDate billedOn) {
        if (amount == null || amount.signum() <= 0)
            throw new IllegalArgumentException("A card transaction amount must be positive.");
        this.cardId = cardId;
        this.reference = reference;
        this.merchantName = merchantName;
        this.merchantCategory = merchantCategory;
        this.transactionType = transactionType;
        this.amount = amount;
        this.currencyCode = "INR";
        this.status = status;
        this.postedAt = postedAt;
        this.billedOn = billedOn;
    }

    public Long getCardTransactionId() {
        return cardTransactionId;
    }

    public Long getCardId() {
        return cardId;
    }

    public String getReference() {
        return reference;
    }

    public String getMerchantName() {
        return merchantName;
    }

    public String getMerchantCategory() {
        return merchantCategory;
    }

    public String getTransactionType() {
        return transactionType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public String getStatus() {
        return status;
    }

    public LocalDateTime getPostedAt() {
        return postedAt;
    }

    public LocalDate getBilledOn() {
        return billedOn;
    }

    public void convertedToEmi() {
        if (!POSTED.equals(status))
            throw new IllegalStateException("Only a posted purchase can be converted to EMI.");
        status = CONVERTED_TO_EMI;
    }
}
