package com.netbanking.otp.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record OtpVerifyRequest(
        @NotBlank String challengeId, @NotBlank @Pattern(regexp = "^[0-9]{6}$") String code) {}
