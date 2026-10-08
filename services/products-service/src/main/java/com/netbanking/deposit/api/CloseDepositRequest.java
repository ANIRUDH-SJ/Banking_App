package com.netbanking.deposit.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CloseDepositRequest(
        @NotBlank @Size(max = 36) String quoteId,
        @NotBlank @Size(max = 100) String idempotencyKey,
        @NotBlank @Size(max = 100) String otpChallengeId,
        @NotBlank @Pattern(regexp = "[0-9]{6}") String otpCode) {}
