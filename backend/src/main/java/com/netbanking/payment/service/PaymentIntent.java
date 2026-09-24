package com.netbanking.payment.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

final class PaymentIntent {
    private PaymentIntent() { }

    static String transferDigest(Long userId, Long sourceAccountId, Long beneficiaryId, BigDecimal amount) {
        return digest("TRANSFER", userId, sourceAccountId, beneficiaryId, amount, null);
    }

    static String billPaymentDigest(Long userId, Long sourceAccountId, Long billerId, BigDecimal amount, String billReference) {
        return digest("BILL_PAYMENT", userId, sourceAccountId, billerId, amount, billReference);
    }

    static String transferFingerprint(Long userId, Long sourceAccountId, Long beneficiaryId, BigDecimal amount, String narration) {
        return digest("TRANSFER_REQUEST", userId, sourceAccountId, beneficiaryId, amount, narration);
    }

    static String billPaymentFingerprint(Long userId, Long sourceAccountId, Long billerId, BigDecimal amount, String billReference) {
        return digest("BILL_PAYMENT_REQUEST", userId, sourceAccountId, billerId, amount, billReference);
    }

    private static String digest(String type, Long userId, Long sourceAccountId, Long targetId, BigDecimal amount, String detail) {
        String canonical = type + "|" + userId + "|" + sourceAccountId + "|" + targetId + "|"
                + amount.stripTrailingZeros().toPlainString() + "|" + (detail == null ? "" : detail.strip());
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte value : bytes) result.append(String.format("%02x", value));
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }
}
