package com.netbanking.contracts;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

public final class RequestFingerprint {
    private RequestFingerprint() {}

    public static String of(Object... values) {
        StringBuilder canonical = new StringBuilder();
        for (Object value : values) {
            String text =
                    value == null
                            ? ""
                            : value instanceof BigDecimal amount
                                    ? amount.stripTrailingZeros().toPlainString()
                                    : value.toString();
            canonical.append(text.length()).append(':').append(text);
        }
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
