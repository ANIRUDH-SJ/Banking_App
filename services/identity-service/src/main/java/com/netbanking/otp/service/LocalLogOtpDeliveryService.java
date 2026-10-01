package com.netbanking.otp.service;

import com.netbanking.otp.domain.OtpPurpose;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@Profile("local")
public class LocalLogOtpDeliveryService implements OtpDeliveryService {
    private static final Logger log = LoggerFactory.getLogger(LocalLogOtpDeliveryService.class);

    @Override
    public void deliver(
            Long userId,
            String challengeId,
            OtpPurpose purpose,
            String code,
            Instant expiresAt) {
        log.warn(
                "Local OTP generated for user {} purpose {} challenge {}; the code is suppressed.",
                userId,
                purpose,
                challengeId);
    }
}
