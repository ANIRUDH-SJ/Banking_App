package com.netbanking.discovery;

import jakarta.validation.constraints.*;

import org.springframework.stereotype.Service;

@Service
public class OtpClient {
    private final ServiceHttpClient client;

    public OtpClient(ServiceHttpClient client) {
        this.client = client;
    }

    public Challenge issue(Long userId, String purpose, String digest) {
        return client.post(
                "identity-service",
                "/internal/otp/challenges",
                new Issue(userId, purpose, digest),
                Challenge.class);
    }

    public void authorize(
            String operationId,
            Long userId,
            String challengeId,
            String code,
            String purpose,
            String digest) {
        client.post(
                "identity-service",
                "/internal/otp/authorizations",
                new Authorization(operationId, userId, challengeId, code, purpose, digest),
                Void.class);
    }

    public record Challenge(String challengeId) {}

    public record Issue(
            @NotNull @Positive Long userId,
            @NotBlank @Pattern(regexp = "FUND_TRANSFER|BILL_PAYMENT|BENEFICIARY_ACTIVATION")
                    String purpose,
            @NotBlank @Pattern(regexp = "[a-f0-9]{64}") String intentDigest) {}

    public record Authorization(
            @NotBlank @Size(max = 64) String operationId,
            @NotNull @Positive Long userId,
            @NotBlank @Size(max = 100) String challengeId,
            @NotBlank @Pattern(regexp = "[0-9]{6}") String code,
            @NotBlank @Pattern(regexp = "FUND_TRANSFER|BILL_PAYMENT|BENEFICIARY_ACTIVATION")
                    String purpose,
            @NotBlank @Pattern(regexp = "[a-f0-9]{64}") String intentDigest) {}
}
