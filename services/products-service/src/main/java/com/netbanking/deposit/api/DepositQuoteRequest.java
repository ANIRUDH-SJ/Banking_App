package com.netbanking.deposit.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record DepositQuoteRequest(
        @NotNull Long sourceAccountId,
        @NotNull String kind,
        @NotNull @DecimalMin("0.01") BigDecimal amount,
        @NotNull Integer termMonths) {}
