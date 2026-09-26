package com.netbanking.totp.api;

import com.netbanking.auth.api.LoginRequest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record TotpConfirmRequest(
        @Valid LoginRequest credentials, @NotBlank @Pattern(regexp = "^[0-9]{6}$") String code) {}
