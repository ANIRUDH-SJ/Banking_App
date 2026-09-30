package com.netbanking.otp.service;

import com.netbanking.common.exception.ResourceNotFoundException;
import com.netbanking.customer.repository.CustomerRepository;
import com.netbanking.discovery.OtpDeliveryClient;
import com.netbanking.otp.domain.OtpPurpose;
import com.netbanking.user.repository.AppUserRepository;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@Profile("!local")
public class NotificationOtpDeliveryService implements OtpDeliveryService {
    private final AppUserRepository users;
    private final CustomerRepository customers;
    private final OtpDeliveryClient delivery;

    public NotificationOtpDeliveryService(
            AppUserRepository users,
            CustomerRepository customers,
            OtpDeliveryClient delivery) {
        this.users = users;
        this.customers = customers;
        this.delivery = delivery;
    }

    @Override
    public void deliver(
            Long userId,
            String challengeId,
            OtpPurpose purpose,
            String code,
            Instant expiresAt) {
        var user =
                users.findById(userId)
                        .orElseThrow(
                                () -> new ResourceNotFoundException("OTP recipient was not found."));
        String mobileNumber =
                customers.findByUserId(userId).map(customer -> customer.getMobileNumber()).orElse(null);
        delivery.deliver(
                new OtpDeliveryClient.Command(
                        challengeId,
                        userId,
                        user.getEmail(),
                        mobileNumber,
                        purpose.name(),
                        code,
                        expiresAt));
    }
}
