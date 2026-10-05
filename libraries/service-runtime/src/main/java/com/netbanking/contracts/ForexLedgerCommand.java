package com.netbanking.contracts;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

/** Immutable instruction for an atomic debit and foreign-currency credit. */
public record ForexLedgerCommand(
        @NotBlank @Size(max = 64) String operationId,
        @NotNull @Positive Long userId,
        @NotNull @Positive Long sourceAccountId,
        @NotNull @Positive Long destinationAccountId,
        @NotNull @DecimalMin("0.0001") @Digits(integer = 15, fraction = 4) BigDecimal sourceAmount,
        @NotNull @DecimalMin("0.0001") @Digits(integer = 15, fraction = 4) BigDecimal destinationAmount,
        @NotBlank @Pattern(regexp = "[A-Z]{3}") String sourceCurrency,
        @NotBlank @Pattern(regexp = "[A-Z]{3}") String destinationCurrency) {}
