package com.netbanking.payment.domain;
import jakarta.persistence.*;
@Entity @Table(name = "bill_payment")
public class BillPayment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "bill_payment_id") private Long billPaymentId;
    @Column(name = "transaction_id", nullable = false) private Long transactionId; @Column(name = "source_account_id", nullable = false) private Long sourceAccountId;
    @Column(name = "biller_id", nullable = false) private Long billerId; @Column(name = "initiated_by_user_id", nullable = false) private Long initiatedByUserId;
    @Column(name = "bill_reference", nullable = false) private String billReference; @Column(name = "idempotency_key", nullable = false) private String idempotencyKey; @Column(name = "payment_status", nullable = false) private String paymentStatus;
    @Column(name = "request_fingerprint", nullable = false, length = 64) private String requestFingerprint;
    protected BillPayment() { }
    public BillPayment(Long transactionId, Long sourceAccountId, Long billerId, Long userId, String billReference, String idempotencyKey, String requestFingerprint) { this.transactionId = transactionId; this.sourceAccountId = sourceAccountId; this.billerId = billerId; this.initiatedByUserId = userId; this.billReference = billReference; this.idempotencyKey = idempotencyKey; this.requestFingerprint = requestFingerprint; this.paymentStatus = "COMPLETED"; }
    public Long getBillPaymentId() { return billPaymentId; } public Long getTransactionId() { return transactionId; } public String getPaymentStatus() { return paymentStatus; } public boolean matchesRequest(String fingerprint) { return requestFingerprint.equals(fingerprint); }
}
