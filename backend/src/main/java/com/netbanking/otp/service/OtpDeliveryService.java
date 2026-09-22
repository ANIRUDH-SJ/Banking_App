package com.netbanking.otp.service;

import com.netbanking.otp.domain.OtpPurpose;

public interface OtpDeliveryService {
    void deliver(Long userId, OtpPurpose purpose, String code);
}
