package com.netbanking.payment.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record BillPaymentOtpChallengeRequest(
        @NotNull Long sourceAccountId,
        @NotNull Long billerId,
        @NotBlank @Size(max = 150) String billReference,
        @NotNull @DecimalMin("0.01") @Digits(integer = 15, fraction = 4) BigDecimal amount) { }
