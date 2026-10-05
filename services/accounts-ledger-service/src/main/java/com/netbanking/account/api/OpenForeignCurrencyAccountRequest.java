package com.netbanking.account.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record OpenForeignCurrencyAccountRequest(
        @NotBlank @Pattern(regexp = "[A-Z]{3}") String currencyCode) {}
