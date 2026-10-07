package com.netbanking.card.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record CardResponse(
        Long cardId,
        Long accountId,
        String maskedCardNumber,
        String cardType,
        String cardNetwork,
        Integer expiryMonth,
        Integer expiryYear,
        String status,
        LocalDateTime activatedAt,
        String lastFour,
        boolean pinSet,
        LocalDateTime pinLockedUntil,
        boolean revealable,
        Credit credit) {

    /** Present for credit cards with a credit account; amounts are in INR. */
    public record Credit(
            BigDecimal creditLimit,
            BigDecimal availableCredit,
            BigDecimal outstandingBalance,
            BigDecimal statementBalance,
            BigDecimal minimumDue,
            LocalDate statementDate,
            LocalDate paymentDueDate) {}
}
