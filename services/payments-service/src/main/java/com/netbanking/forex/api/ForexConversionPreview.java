package com.netbanking.forex.api;

import java.math.BigDecimal;
import java.time.Instant;

public record ForexConversionPreview(
        String fromCurrency,
        String toCurrency,
        BigDecimal amount,
        BigDecimal exchangeRate,
        BigDecimal inverseRate,
        BigDecimal convertedAmount,
        String rateSource,
        Instant asOf) {}
