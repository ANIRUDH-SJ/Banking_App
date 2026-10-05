package com.netbanking.deposit.api;

import jakarta.validation.constraints.NotBlank;

public record OpenDepositRequest(@NotBlank String quoteId, @NotBlank String idempotencyKey) {}
