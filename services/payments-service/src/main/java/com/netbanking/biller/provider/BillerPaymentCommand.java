package com.netbanking.biller.provider;

import java.math.BigDecimal;

public record BillerPaymentCommand(
        String operationId,
        String billerCode,
        String customerReference,
        BigDecimal amount,
        String currencyCode) {}
