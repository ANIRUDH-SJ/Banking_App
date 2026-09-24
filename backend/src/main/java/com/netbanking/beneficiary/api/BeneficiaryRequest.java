package com.netbanking.beneficiary.api;
import jakarta.validation.constraints.*;
public record BeneficiaryRequest(
        @NotBlank @Size(max = 100) String nickname,
        @NotBlank @Size(max = 200) String beneficiaryName,
        @NotBlank @Pattern(regexp = "^[0-9]{10,20}$") String accountNumber,
        @NotBlank @Pattern(regexp = "^[A-Za-z]{4}0[A-Za-z0-9]{6}$") String ifscCode,
        @NotBlank @Size(max = 200) String bankName) { }
