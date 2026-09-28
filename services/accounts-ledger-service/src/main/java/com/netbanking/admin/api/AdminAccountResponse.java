package com.netbanking.admin.api;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record AdminAccountResponse(
        Long accountId,
        Long branchId,
        String accountNumber,
        String accountType,
        String currencyCode,
        String status,
        BigDecimal currentBalance,
        BigDecimal availableBalance,
        LocalDateTime closedAt,
        List<Long> customerIds) {}
