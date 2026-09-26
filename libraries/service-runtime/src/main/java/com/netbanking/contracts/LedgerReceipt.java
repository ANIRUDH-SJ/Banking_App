package com.netbanking.contracts;

import java.math.BigDecimal;

public record LedgerReceipt(
        Long transactionId,
        String reference,
        String status,
        BigDecimal amount,
        String currencyCode) {}
