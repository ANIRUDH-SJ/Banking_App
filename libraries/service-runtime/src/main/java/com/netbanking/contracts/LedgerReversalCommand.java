package com.netbanking.contracts;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record LedgerReversalCommand(
        @NotBlank @Size(max = 64) String operationId,
        @NotNull Long userId,
        @NotNull Long originalTransactionId,
        @NotBlank @Size(max = 500) String reason) {}
