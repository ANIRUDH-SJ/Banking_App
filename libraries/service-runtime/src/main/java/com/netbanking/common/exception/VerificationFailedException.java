package com.netbanking.common.exception;

/**
 * A customer-supplied secret (one-time code or card PIN) did not verify.
 *
 * <p>The session is still valid, so this is reported as 422 (or 423 when locked) rather than
 * 401; clients sign the customer out on 401.
 */
public class VerificationFailedException extends UnauthorizedException {
    private final String code;
    private final String field;
    private final long retryAfterSeconds;

    public VerificationFailedException(String code, String field, String message) {
        this(code, field, message, 0);
    }

    public VerificationFailedException(
            String code, String field, String message, long retryAfterSeconds) {
        super(message);
        this.code = code;
        this.field = field;
        this.retryAfterSeconds = Math.max(0, retryAfterSeconds);
    }

    public String code() {
        return code;
    }

    public String field() {
        return field;
    }

    public boolean locked() {
        return retryAfterSeconds > 0;
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
