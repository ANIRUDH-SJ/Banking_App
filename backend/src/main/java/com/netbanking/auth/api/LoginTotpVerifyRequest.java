package com.netbanking.auth.api;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
public record LoginTotpVerifyRequest(
        @NotBlank String challengeId,
        @NotBlank @Pattern(regexp = "^[0-9]{6}$") String code) {
}
