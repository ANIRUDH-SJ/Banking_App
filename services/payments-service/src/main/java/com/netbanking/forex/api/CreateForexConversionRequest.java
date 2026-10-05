package com.netbanking.forex.api;

import jakarta.validation.constraints.*;

public record CreateForexConversionRequest(
        @NotBlank String quoteId,
        @NotBlank @Size(max = 64) String idempotencyKey,
        @NotBlank String otpChallengeId,
        @NotBlank @Pattern(regexp = "[0-9]{6}") String otpCode) {}
