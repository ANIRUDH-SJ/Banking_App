package com.netbanking.payment.domain;
import jakarta.persistence.*;
@Entity @Table(name = "bill_payment")
public class BillPayment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "bill_payment_id") private Long billPaymentId;
    @Column(name = "transaction_id", nullable = false) private Long transactionId; @Column(name = "source_account_id", nullable = false) private Long sourceAccountId;
    @Column(name = "biller_id", nullable = false) private Long billerId; @Column(name = "initiated_by_user_id", nullable = false) private Long initiatedByUserId;
    @Column(name = "bill_reference", nullable = false) private String billReference; @Column(name = "idempotency_key", nullable = false) private String idempotencyKey; @Column(name = "request_fingerprint", length = 64) private String requestFingerprint; @Column(name = "payment_status", nullable = false) private String paymentStatus;
    protected BillPayment() { }
    public BillPayment(Long transactionId, Long sourceAccountId, Long billerId, Long userId,
                       String billReference, String idempotencyKey, String requestFingerprint) { this.transactionId = transactionId; this.sourceAccountId = sourceAccountId; this.billerId = billerId; this.initiatedByUserId = userId; this.billReference = billReference; this.idempotencyKey = idempotencyKey; this.requestFingerprint = requestFingerprint; this.paymentStatus = "COMPLETED"; }
    public Long getBillPaymentId() { return billPaymentId; } public Long getTransactionId() { return transactionId; } public Long getSourceAccountId() { return sourceAccountId; } public Long getBillerId() { return billerId; } public String getBillReference() { return billReference; } public String getPaymentStatus() { return paymentStatus; }
    public String getRequestFingerprint() { return requestFingerprint; }
    public boolean matchesRequest(String fingerprint) { return fingerprint != null && fingerprint.equals(requestFingerprint); }
    public void bindLegacyRequest(String fingerprint) { if (requestFingerprint == null) requestFingerprint = fingerprint; }
}
