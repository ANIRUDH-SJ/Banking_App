package com.netbanking.transaction.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "bank_transaction")
public class BankTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "transaction_id")
    private Long transactionId;

    @Column(name = "transaction_reference", nullable = false, unique = true)
    private String transactionReference;

    @Column(name = "debit_account_id")
    private Long debitAccountId;

    @Column(name = "credit_account_id")
    private Long creditAccountId;

    @Column(name = "beneficiary_id")
    private Long beneficiaryId;

    @Column(name = "initiated_by_user_id")
    private Long initiatedByUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false)
    private TransactionType transactionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_status", nullable = false)
    private TransactionStatus transactionStatus;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "currency_code", nullable = false, columnDefinition = "CHAR(3)")
    private String currencyCode;

    @Column(name = "narration")
    private String narration;

    @Column(name = "initiated_at", nullable = false)
    private LocalDateTime initiatedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "failure_reason")
    private String failureReason;

    protected BankTransaction() {
    }

    public BankTransaction(String transactionReference, Long debitAccountId, Long creditAccountId,
                           Long beneficiaryId, Long initiatedByUserId, TransactionType transactionType,
                           BigDecimal amount, String currencyCode, String narration) {
        this.transactionReference = transactionReference;
        this.debitAccountId = debitAccountId;
        this.creditAccountId = creditAccountId;
        this.beneficiaryId = beneficiaryId;
        this.initiatedByUserId = initiatedByUserId;
        this.transactionType = transactionType;
        this.transactionStatus = TransactionStatus.PENDING;
        this.amount = amount;
        this.currencyCode = currencyCode;
        this.narration = narration;
        this.initiatedAt = LocalDateTime.now();
    }

    public Long getTransactionId() { return transactionId; }
    public String getTransactionReference() { return transactionReference; }
    public Long getDebitAccountId() { return debitAccountId; }
    public Long getCreditAccountId() { return creditAccountId; }
    public Long getBeneficiaryId() { return beneficiaryId; }
    public Long getInitiatedByUserId() { return initiatedByUserId; }
    public TransactionType getTransactionType() { return transactionType; }
    public TransactionStatus getTransactionStatus() { return transactionStatus; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrencyCode() { return currencyCode; }
    public String getNarration() { return narration; }
    public LocalDateTime getInitiatedAt() { return initiatedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public String getFailureReason() { return failureReason; }

    public void transitionTo(TransactionStatus newStatus, String failureReason) {
        if (!canTransitionTo(newStatus)) {
            throw new IllegalStateException(
                    "Transaction cannot move from " + transactionStatus + " to " + newStatus + ".");
        }
        if (newStatus == TransactionStatus.FAILED
                && (failureReason == null || failureReason.isBlank())) {
            throw new IllegalArgumentException("A failure reason is required for a failed transaction.");
        }
        transactionStatus = newStatus;
        this.failureReason = newStatus == TransactionStatus.FAILED ? failureReason.strip() : null;
        if ((newStatus == TransactionStatus.COMPLETED || newStatus == TransactionStatus.REVERSED)
                && completedAt == null) {
            completedAt = LocalDateTime.now();
        }
    }

    private boolean canTransitionTo(TransactionStatus newStatus) {
        return switch (transactionStatus) {
            case PENDING -> newStatus == TransactionStatus.PROCESSING || newStatus == TransactionStatus.FAILED;
            case PROCESSING -> newStatus == TransactionStatus.COMPLETED || newStatus == TransactionStatus.FAILED;
            case COMPLETED -> newStatus == TransactionStatus.REVERSED;
            case FAILED, REVERSED -> false;
        };
    }
}
