package com.netbanking.discovery;

import com.netbanking.common.exception.VerificationFailedException;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import org.springframework.stereotype.Service;

/**
 * Asks products-service to verify a debit-card PIN for a payment. The PIN stays encrypted for
 * products-service; the caller only forwards the ciphertext.
 */
@Service
public class CardPinClient {
    private final ServiceHttpClient client;

    public CardPinClient(ServiceHttpClient client) {
        this.client = client;
    }

    public void verify(Verification verification) {
        try {
            client.post(
                    "products-service", "/internal/cards/pin-verifications", verification, Void.class);
        } catch (DownstreamRejectedException rejected) {
            int status = rejected.getStatusCode().value();
            if ((status == 422 || status == 423) && rejected.code() != null && rejected.code().startsWith("PIN_"))
                throw new VerificationFailedException(
                        rejected.code(), "pin", rejected.detail(), status == 423 ? Math.max(1, rejected.retryAfterSeconds()) : 0);
            if (status == 409 && rejected.detail() != null)
                throw new com.netbanking.common.exception.ConflictException(rejected.detail());
            throw rejected;
        }
    }

    public record Verification(
            @NotNull @Positive Long userId,
            @NotNull @Positive Long cardId,
            @NotNull @Positive Long accountId,
            @NotBlank @Size(max = 32) String pinKeyId,
            @NotBlank @Size(max = 1024) String encryptedPin,
            @NotBlank @Pattern(regexp = "FUND_TRANSFER|BILL_PAYMENT") String purpose) {}
}
