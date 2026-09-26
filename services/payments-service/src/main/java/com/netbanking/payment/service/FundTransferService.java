package com.netbanking.payment.service;

import com.netbanking.payment.api.FundTransferRequest;
import com.netbanking.payment.api.OtpChallengeResponse;
import com.netbanking.payment.api.PaymentReceiptResponse;
import com.netbanking.payment.api.TransferOtpChallengeRequest;

import org.springframework.stereotype.Service;

@Service
public class FundTransferService {
    private final PaymentService paymentService;

    public FundTransferService(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    public OtpChallengeResponse issueOtp(Long userId, TransferOtpChallengeRequest request) {
        return paymentService.issueTransferOtp(userId, request);
    }

    public PaymentReceiptResponse transfer(Long userId, FundTransferRequest request) {
        return paymentService.transfer(userId, request);
    }
}
