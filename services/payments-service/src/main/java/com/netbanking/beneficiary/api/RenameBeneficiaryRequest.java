package com.netbanking.beneficiary.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RenameBeneficiaryRequest(@NotBlank @Size(max = 100) String nickname) {}
