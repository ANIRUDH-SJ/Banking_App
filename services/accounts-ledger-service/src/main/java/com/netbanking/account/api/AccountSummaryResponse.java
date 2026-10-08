package com.netbanking.account.api;

import java.math.BigDecimal;

public record AccountSummaryResponse(
        Long accountId,
        Long branchId,
        String accountNumber,
        String accountType,
        String currencyCode,
        String accountStatus,
        BigDecimal currentBalance,
        BigDecimal availableBalance,
        String nickname,
        String bankName,
        String branchName,
        String branchCity,
        String branchState,
        String ifscCode) {}
