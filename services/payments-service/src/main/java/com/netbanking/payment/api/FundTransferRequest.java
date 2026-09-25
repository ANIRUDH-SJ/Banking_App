package com.netbanking.payment.api;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record FundTransferRequest(
        @NotNull Long sourceAccountId,
        @NotNull Long beneficiaryId,
        @NotNull @DecimalMin(value = "0.01") @Digits(integer = 15, fraction = 4) BigDecimal amount,
        @Size(max = 500) String narration,
        @NotBlank @Size(max = 100) String idempotencyKey,
        @NotBlank String otpChallengeId,
        @Pattern(regexp = "^[0-9]{6}$") String otpCode) {}
