package com.netbanking.deposit.api;

import java.math.BigDecimal;

public record DepositClosureResponse(
        String depositId, String status, BigDecimal payoutAmount, String payoutReference) {}
