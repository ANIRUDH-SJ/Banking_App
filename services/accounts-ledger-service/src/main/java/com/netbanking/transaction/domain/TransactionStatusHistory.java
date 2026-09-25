package com.netbanking.transaction.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "bank_transaction_status_history")
public class TransactionStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "status_history_id")
    private Long statusHistoryId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false)
    private BankTransaction transaction;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status")
    private TransactionStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false)
    private TransactionStatus newStatus;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "changed_by_user_id")
    private Long changedByUserId;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;

    protected TransactionStatusHistory() {}

    public TransactionStatusHistory(
            BankTransaction transaction,
            TransactionStatus previousStatus,
            TransactionStatus newStatus,
            String failureReason,
            Long changedByUserId) {
        this.transaction = transaction;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.failureReason = failureReason;
        this.changedByUserId = changedByUserId;
        this.changedAt = LocalDateTime.now();
    }

    public Long getStatusHistoryId() {
        return statusHistoryId;
    }

    public TransactionStatus getPreviousStatus() {
        return previousStatus;
    }

    public TransactionStatus getNewStatus() {
        return newStatus;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Long getChangedByUserId() {
        return changedByUserId;
    }

    public LocalDateTime getChangedAt() {
        return changedAt;
    }
}
