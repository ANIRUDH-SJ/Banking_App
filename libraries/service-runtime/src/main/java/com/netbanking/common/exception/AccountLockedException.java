package com.netbanking.common.exception;

public class AccountLockedException extends UnauthorizedException {
    private final long retryAfterSeconds;

    public AccountLockedException(long retryAfterSeconds) {
        super("Account is temporarily locked.");
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
