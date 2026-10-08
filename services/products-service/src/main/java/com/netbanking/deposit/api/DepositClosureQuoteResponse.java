package com.netbanking.deposit.api;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record DepositClosureQuoteResponse(
        String quoteId,
        String depositId,
        BigDecimal principal,
        BigDecimal interest,
        BigDecimal annualRatePercent,
        BigDecimal payoutAmount,
        OffsetDateTime expiresAt) {}
