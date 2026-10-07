package com.netbanking.payment.api;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentHistoryResponse(
        Long paymentId,
        String kind,
        String status,
        Long transactionId,
        String transactionReference,
        BigDecimal amount,
        String currencyCode,
        Long sourceAccountId,
        Long beneficiaryId,
        Long billerId,
        String billReference,
        String narration,
        Instant createdAt,
        String beneficiaryNickname,
        String billerName) {

    public PaymentHistoryResponse named(String beneficiaryNickname, String billerName) {
        return new PaymentHistoryResponse(
                paymentId, kind, status, transactionId, transactionReference, amount, currencyCode,
                sourceAccountId, beneficiaryId, billerId, billReference, narration, createdAt,
                beneficiaryNickname, billerName);
    }
}
