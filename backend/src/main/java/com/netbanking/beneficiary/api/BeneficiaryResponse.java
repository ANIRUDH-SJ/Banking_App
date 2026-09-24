package com.netbanking.beneficiary.api;
import java.time.Instant;
public record BeneficiaryResponse(Long beneficiaryId, String nickname, String beneficiaryName, String maskedAccountNumber,
                                  String ifscCode, String bankName, String status, Instant activatedAt) { }
