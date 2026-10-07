package com.netbanking.deposit.api;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record DepositQuoteResponse(String quoteId, String kind, BigDecimal amount,
        int termMonths, BigDecimal annualRatePercent, BigDecimal estimatedMaturity,
        OffsetDateTime expiresAt) {}
