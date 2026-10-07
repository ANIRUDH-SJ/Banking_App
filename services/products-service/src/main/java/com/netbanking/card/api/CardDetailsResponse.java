package com.netbanking.card.api;

/** The full card number, for display only; CVV is never stored or returned. */
public record CardDetailsResponse(
        Long cardId, String cardNumber, Integer expiryMonth, Integer expiryYear, int revealSeconds) {}
