package com.netbanking.otp.service;

import com.netbanking.otp.domain.OtpPurpose;

import java.time.Instant;

public interface OtpDeliveryService {
    void deliver(
            Long userId,
            String challengeId,
            OtpPurpose purpose,
            String code,
            Instant expiresAt);
}
