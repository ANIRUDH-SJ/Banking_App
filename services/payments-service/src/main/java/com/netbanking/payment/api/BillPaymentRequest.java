package com.netbanking.payment.api;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record BillPaymentRequest(
        @NotNull Long sourceAccountId,
        @NotNull Long billerId,
        @NotBlank @Size(max = 150) String billReference,
        @NotNull @DecimalMin(value = "0.01") @Digits(integer = 15, fraction = 4) BigDecimal amount,
        @NotBlank @Size(max = 100) String idempotencyKey,
        @NotBlank String otpChallengeId,
        @Pattern(regexp = "^[0-9]{6}$") String otpCode) {}
