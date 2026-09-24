package com.netbanking.payment.service;

import java.math.BigDecimal;

public record PaymentCompletedEvent(Long userId, String paymentType, String transactionReference, BigDecimal amount, String currencyCode) { }
