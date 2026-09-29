package com.netbanking.auth.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetChallengeRequest(
        @NotBlank @Size(min = 3, max = 254) String usernameOrEmail) {}
