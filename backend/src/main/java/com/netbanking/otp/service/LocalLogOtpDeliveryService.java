package com.netbanking.otp.service;

import com.netbanking.otp.domain.OtpPurpose;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("local")
public class LocalLogOtpDeliveryService implements OtpDeliveryService {
    private static final Logger log = LoggerFactory.getLogger(LocalLogOtpDeliveryService.class);
    @Override
    public void deliver(Long userId, OtpPurpose purpose, String code) {
        log.warn("LOCAL DEVELOPMENT OTP for user {} purpose {}: {}. Replace this adapter before deployment.", userId, purpose, code);
    }
}
