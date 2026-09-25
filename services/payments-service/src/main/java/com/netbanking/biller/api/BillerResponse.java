package com.netbanking.biller.api;

import java.math.BigDecimal;

public record BillerResponse(
        Long billerId,
        String code,
        String name,
        String category,
        String referenceLabel,
        BigDecimal minAmount,
        BigDecimal maxAmount) {}
