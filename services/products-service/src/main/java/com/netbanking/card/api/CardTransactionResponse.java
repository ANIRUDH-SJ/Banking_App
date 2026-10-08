package com.netbanking.card.api;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CardTransactionResponse(
        Long transactionId,
        String reference,
        String merchantName,
        String category,
        String type,
        BigDecimal amount,
        String currencyCode,
        String status,
        LocalDateTime postedAt,
        boolean billed,
        boolean emiEligible,
        EmiPlanResponse emiPlan) {}
