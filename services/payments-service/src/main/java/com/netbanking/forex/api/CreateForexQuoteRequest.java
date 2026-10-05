package com.netbanking.forex.api;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record CreateForexQuoteRequest(
        @NotNull @Positive Long sourceAccountId,
        @NotNull @Positive Long destinationAccountId,
        @NotNull @DecimalMin("0.0001") @Digits(integer = 15, fraction = 4) BigDecimal sourceAmount) {}
