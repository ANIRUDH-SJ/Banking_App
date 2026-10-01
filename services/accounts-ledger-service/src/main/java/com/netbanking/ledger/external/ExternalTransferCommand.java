package com.netbanking.ledger.external;

import java.math.BigDecimal;

public record ExternalTransferCommand(
        String operationId,
        String destinationAccountNumber,
        String destinationIfsc,
        BigDecimal amount,
        String currencyCode,
        String narration) {}
