package com.netbanking.notification.api;

import com.netbanking.discovery.OtpDeliveryClient;
import com.netbanking.notification.service.OtpChannelDeliveryService;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@PreAuthorize("hasAuthority('SERVICE_identity-service')")
public class InternalOtpDeliveryController {
    private final OtpChannelDeliveryService delivery;

    public InternalOtpDeliveryController(OtpChannelDeliveryService delivery) {
        this.delivery = delivery;
    }

    @PostMapping("/internal/otp-deliveries")
    public OtpDeliveryClient.Receipt deliver(
            @Valid @RequestBody OtpDeliveryClient.Command command, Authentication caller) {
        if (!"identity-service".equals(caller.getName())) {
            throw new SecurityException("Only the identity service can deliver OTP codes.");
        }
        return delivery.deliver(command);
    }
}
