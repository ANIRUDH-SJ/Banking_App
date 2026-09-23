package com.netbanking.loan.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "loan_payment")
public class LoanPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "loan_payment_id")
    private Long loanPaymentId;

    @Column(name = "loan_id", nullable = false)
    private Long loanId;

    @Column(name = "source_account_id", nullable = false)
    private Long sourceAccountId;

    @Column(name = "transaction_id", nullable = false, unique = true)
    private Long transactionId;

    @Column(name = "transaction_reference", nullable = false, unique = true, length = 50)
    private String transactionReference;

    @Column(name = "idempotency_key", nullable = false, length = 64)
    private String idempotencyKey;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency_code", nullable = false, columnDefinition = "CHAR(3)")
    private String currencyCode;

    @Column(name = "outstanding_after", nullable = false, precision = 19, scale = 4)
    private BigDecimal outstandingAfter;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 20)
    private LoanPaymentStatus paymentStatus;

    @Column(name = "paid_at", nullable = false)
    private LocalDateTime paidAt;

    protected LoanPayment() {
    }

    public LoanPayment(Long loanId, Long sourceAccountId, Long transactionId,
                       String transactionReference, String idempotencyKey, BigDecimal amount,
                       String currencyCode, BigDecimal outstandingAfter) {
        this.loanId = loanId;
        this.sourceAccountId = sourceAccountId;
        this.transactionId = transactionId;
        this.transactionReference = transactionReference;
        this.idempotencyKey = idempotencyKey;
        this.amount = amount;
        this.currencyCode = currencyCode;
        this.outstandingAfter = outstandingAfter;
        this.paymentStatus = LoanPaymentStatus.COMPLETED;
    }

    @PrePersist
    void setPaidAt() {
        if (paidAt == null) {
            paidAt = LocalDateTime.now();
        }
    }

    public Long getLoanPaymentId() { return loanPaymentId; }
    public Long getLoanId() { return loanId; }
    public Long getSourceAccountId() { return sourceAccountId; }
    public Long getTransactionId() { return transactionId; }
    public String getTransactionReference() { return transactionReference; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrencyCode() { return currencyCode; }
    public BigDecimal getOutstandingAfter() { return outstandingAfter; }
    public LoanPaymentStatus getPaymentStatus() { return paymentStatus; }
    public LocalDateTime getPaidAt() { return paidAt; }
}
