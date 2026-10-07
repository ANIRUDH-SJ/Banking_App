package com.netbanking.otp.service;

import com.netbanking.otp.domain.OtpPurpose;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Instant;

/** Writes challenges to the log instead of sending them; for tests and isolated identity runs. */
@Service
@ConditionalOnProperty(name = "app.security.otp.delivery", havingValue = "log")
public class LocalLogOtpDeliveryService implements OtpDeliveryService {
    private static final Logger log = LoggerFactory.getLogger(LocalLogOtpDeliveryService.class);

    private final boolean revealCode;

    public LocalLogOtpDeliveryService(
            @Value("${app.security.otp.log-codes:false}") boolean revealCode) {
        this.revealCode = revealCode;
    }

    @Override
    public void deliver(
            Long userId,
            String challengeId,
            OtpPurpose purpose,
            String code,
            Instant expiresAt) {
        if (revealCode) {
            log.warn(
                    "DEVELOPMENT ONLY: OTP {} for user {} purpose {} challenge {} expires at {}.",
                    code,
                    userId,
                    purpose,
                    challengeId,
                    expiresAt);
            return;
        }
        log.warn(
                "Local OTP generated for user {} purpose {} challenge {}; the code is suppressed.",
                userId,
                purpose,
                challengeId);
    }
}
