package com.netbanking.payment.service;

import com.netbanking.payment.api.FundTransferRequest;
import com.netbanking.payment.api.OtpChallengeRequest;
import com.netbanking.payment.api.OtpChallengeResponse;
import com.netbanking.payment.api.PaymentReceiptResponse;
import org.springframework.stereotype.Service;

/** Feature 13 boundary: transfer-specific API orchestration. */
@Service
public class FundTransferService {
    private final PaymentService paymentService;
    public FundTransferService(PaymentService paymentService) { this.paymentService = paymentService; }
    public OtpChallengeResponse issueOtp(Long userId, OtpChallengeRequest request) { return paymentService.issueTransferOtp(userId, request); }
    public PaymentReceiptResponse transfer(Long userId, FundTransferRequest request) { return paymentService.transfer(userId, request); }
}
