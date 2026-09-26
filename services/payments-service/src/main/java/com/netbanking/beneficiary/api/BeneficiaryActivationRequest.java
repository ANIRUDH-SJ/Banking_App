package com.netbanking.beneficiary.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record BeneficiaryActivationRequest(
        @NotBlank String otpChallengeId,
        @NotBlank @Pattern(regexp = "^[0-9]{6}$") String otpCode) {}
