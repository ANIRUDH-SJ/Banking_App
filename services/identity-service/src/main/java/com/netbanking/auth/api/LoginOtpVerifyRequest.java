package com.netbanking.auth.api;

import com.netbanking.otp.api.OtpVerifyRequest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

public record LoginOtpVerifyRequest(
        @NotBlank String usernameOrEmail, @Valid OtpVerifyRequest otp) {}
