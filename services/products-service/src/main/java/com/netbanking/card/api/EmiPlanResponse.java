package com.netbanking.card.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record EmiPlanResponse(
        Long emiPlanId,
        Long cardId,
        Long transactionId,
        BigDecimal principal,
        int tenureMonths,
        BigDecimal annualInterestRate,
        BigDecimal monthlyInstalment,
        BigDecimal totalInterest,
        BigDecimal totalPayable,
        int instalmentsPaid,
        LocalDate firstDueDate,
        String status,
        LocalDateTime createdAt) {}
