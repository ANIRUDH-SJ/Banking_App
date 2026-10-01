package com.netbanking.otp.service;

public class OtpIssueLimitException extends IllegalStateException {
    public OtpIssueLimitException(String message) {
        super(message);
    }
}
