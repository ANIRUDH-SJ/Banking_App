package com.netbanking.card.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ConvertToEmiRequest(
        @NotNull Integer tenureMonths, @NotBlank @Size(max = 64) String idempotencyKey) {}
