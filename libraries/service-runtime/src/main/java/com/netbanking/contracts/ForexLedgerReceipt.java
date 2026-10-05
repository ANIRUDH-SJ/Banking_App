package com.netbanking.contracts;

import java.math.BigDecimal;

public record ForexLedgerReceipt(
        Long debitTransactionId,
        String debitReference,
        Long creditTransactionId,
        String creditReference,
        BigDecimal sourceAmount,
        BigDecimal destinationAmount) {}
