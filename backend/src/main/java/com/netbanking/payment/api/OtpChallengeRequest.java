package com.netbanking.payment.api;
import jakarta.validation.constraints.NotNull;
public record OtpChallengeRequest(@NotNull Long sourceAccountId) { }
