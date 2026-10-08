package com.netbanking.discovery;

import jakarta.validation.constraints.*;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class OtpDeliveryClient {
    private final ServiceHttpClient client;

    public OtpDeliveryClient(ServiceHttpClient client) {
        this.client = client;
    }

    public Receipt deliver(Command command) {
        try {
            return client.post(
                "notification-service",
                "/internal/otp-deliveries",
                command,
                Receipt.class);
        } catch (DownstreamRejectedException rejected) {
            throw EmailDeliveryErrors.translate(rejected);
        }
    }

    public record Command(
            @NotBlank @Size(max = 100) String challengeId,
            @NotNull @Positive Long userId,
            @NotBlank @Email @Size(max = 254) String email,
            @Pattern(regexp = "\\+?[0-9]{10,15}") String mobileNumber,
            @NotBlank
                    @Pattern(
                            regexp =
                                    "LOGIN|FUND_TRANSFER|BILL_PAYMENT|BENEFICIARY_ACTIVATION|FOREX_CONVERSION|DEPOSIT_CLOSURE|PASSWORD_RESET")
                    String purpose,
            @NotBlank @Pattern(regexp = "[0-9]{6}") String code,
            @NotNull Instant expiresAt) {}

    public record Receipt(String challengeId, List<String> channels) {}
}
