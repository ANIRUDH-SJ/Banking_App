package com.netbanking.contracts;

import java.math.BigDecimal;

public record AccountSnapshot(
        Long accountId,
        String accountNumber,
        String currencyCode,
        String status,
        BigDecimal availableBalance) {}
