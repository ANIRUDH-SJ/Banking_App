package com.netbanking.loan.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateLoanPaymentRequest(
        @NotNull Long sourceAccountId,
        @NotNull @DecimalMin(value = "0.0001") @Digits(integer = 15, fraction = 4)
                BigDecimal amount,
        @NotBlank @Size(min = 8, max = 64) @Pattern(regexp = "[A-Za-z0-9._:-]+")
                String idempotencyKey) {}
