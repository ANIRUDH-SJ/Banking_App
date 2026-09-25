package com.netbanking.payment.api;

import java.math.BigDecimal;

public record PaymentReceiptResponse(
        Long paymentId,
        Long transactionId,
        String transactionReference,
        String status,
        BigDecimal amount,
        String currencyCode) {}
