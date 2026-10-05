package com.netbanking.deposit.api;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record DepositResponse(String depositId, String kind, String status,
        Long sourceAccountId, BigDecimal installmentOrPrincipal, int termMonths,
        BigDecimal annualRatePercent, BigDecimal contributedAmount,
        BigDecimal estimatedMaturity, BigDecimal payoutAmount, String payoutReference,
        int installmentsPaid, LocalDateTime openedAt, LocalDateTime maturityAt,
        LocalDateTime nextDueAt) {}
