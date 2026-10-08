package com.netbanking.payment.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Authorizes a payment with the PIN of the debit card linked to the paying account instead of a
 * one-time code. {@code encryptedPin} is opaque here; only products-service can read it.
 */
public record CardPinAuthorization(
        @NotNull @Positive Long cardId,
        @NotBlank @Size(max = 32) String pinKeyId,
        @NotBlank @Size(max = 1024) String encryptedPin) {}
