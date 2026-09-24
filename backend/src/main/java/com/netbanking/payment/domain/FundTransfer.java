package com.netbanking.payment.domain;
import jakarta.persistence.*;
@Entity @Table(name = "fund_transfer")
public class FundTransfer {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "transfer_id") private Long transferId;
    @Column(name = "transaction_id", nullable = false) private Long transactionId;
    @Column(name = "source_account_id", nullable = false) private Long sourceAccountId;
    @Column(name = "beneficiary_id", nullable = false) private Long beneficiaryId;
    @Column(name = "initiated_by_user_id", nullable = false) private Long initiatedByUserId;
    @Column(name = "idempotency_key", nullable = false) private String idempotencyKey;
    @Column(name = "request_fingerprint", nullable = false, length = 64) private String requestFingerprint;
    @Column(name = "transfer_status", nullable = false) private String transferStatus;
    protected FundTransfer() { }
    public FundTransfer(Long transactionId, Long sourceAccountId, Long beneficiaryId, Long userId, String idempotencyKey, String requestFingerprint) { this.transactionId = transactionId; this.sourceAccountId = sourceAccountId; this.beneficiaryId = beneficiaryId; this.initiatedByUserId = userId; this.idempotencyKey = idempotencyKey; this.requestFingerprint = requestFingerprint; this.transferStatus = "COMPLETED"; }
    public Long getTransferId() { return transferId; } public Long getTransactionId() { return transactionId; } public String getTransferStatus() { return transferStatus; } public boolean matchesRequest(String fingerprint) { return requestFingerprint.equals(fingerprint); }
}
