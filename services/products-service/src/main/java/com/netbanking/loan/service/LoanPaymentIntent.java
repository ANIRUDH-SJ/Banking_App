package com.netbanking.loan.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

final class LoanPaymentIntent {
    private LoanPaymentIntent() {}

    static String fingerprint(Long userId, Long loanId, Long sourceAccountId, BigDecimal amount) {
        String canonical =
                userId
                        + "|"
                        + loanId
                        + "|"
                        + sourceAccountId
                        + "|"
                        + amount.stripTrailingZeros().toPlainString();
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }
}
