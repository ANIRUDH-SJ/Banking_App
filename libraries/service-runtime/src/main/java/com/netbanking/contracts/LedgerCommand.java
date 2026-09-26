package com.netbanking.contracts;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record LedgerCommand(
        @NotBlank @Size(max = 64) String operationId,
        @NotNull Long userId,
        @NotNull Long sourceAccountId,
        @Size(max = 20) String destinationAccountNumber,
        @Size(max = 20) String destinationIfsc,
        @NotBlank @Pattern(regexp = "TRANSFER|WITHDRAWAL|LOAN_PAYMENT") String type,
        @NotNull @DecimalMin("0.0001") @Digits(integer = 15, fraction = 4) BigDecimal amount,
        @NotBlank @Pattern(regexp = "[A-Z]{3}") String currencyCode,
        @Size(max = 500) String narration) {}
