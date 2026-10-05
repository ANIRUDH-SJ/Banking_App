package com.netbanking.forex.api;

import java.math.BigDecimal;

public record ForexConversionResponse(
        Long conversionId, String quoteId, String status,
        BigDecimal sourceAmount, String sourceCurrency,
        BigDecimal destinationAmount, String destinationCurrency,
        String debitReference, String creditReference) {}
