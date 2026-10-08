package com.netbanking.card.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Both PINs are encrypted with the key from {@code GET /api/v1/cards/pin-key}. */
public record SetCardPinRequest(
        @NotBlank @Size(max = 32) String pinKeyId,
        @NotBlank @Size(max = 1024) String encryptedPin,
        @Size(max = 1024) String encryptedCurrentPin) {}
