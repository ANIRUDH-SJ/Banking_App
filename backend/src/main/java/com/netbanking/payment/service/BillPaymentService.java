package com.netbanking.payment.service;

import com.netbanking.payment.api.BillPaymentRequest;
import com.netbanking.payment.api.OtpChallengeRequest;
import com.netbanking.payment.api.OtpChallengeResponse;
import com.netbanking.payment.api.PaymentReceiptResponse;
import org.springframework.stereotype.Service;

/** Feature 15 boundary: bill-payment-specific API orchestration. */
@Service
public class BillPaymentService {
    private final PaymentService paymentService;
    public BillPaymentService(PaymentService paymentService) { this.paymentService = paymentService; }
    public OtpChallengeResponse issueOtp(Long userId, OtpChallengeRequest request) { return paymentService.issueBillPaymentOtp(userId, request); }
    public PaymentReceiptResponse pay(Long userId, BillPaymentRequest request) { return paymentService.payBill(userId, request); }
}
