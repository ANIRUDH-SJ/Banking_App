package com.netbanking.forex.api;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record ForexQuoteResponse(
        String quoteId, Long sourceAccountId, Long destinationAccountId,
        String sourceCurrency, String destinationCurrency,
        BigDecimal sourceAmount, BigDecimal destinationAmount, BigDecimal exchangeRate,
        String rateSource, OffsetDateTime expiresAt) {}
