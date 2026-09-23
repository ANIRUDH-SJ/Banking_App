package com.netbanking.card.api;

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
        LocalDateTime activatedAt) {
}
