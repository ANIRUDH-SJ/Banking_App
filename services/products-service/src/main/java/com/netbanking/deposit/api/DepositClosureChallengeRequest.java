package com.netbanking.deposit.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DepositClosureChallengeRequest(@NotBlank @Size(max = 36) String quoteId) {}
