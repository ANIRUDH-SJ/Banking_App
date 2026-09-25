package com.netbanking.payment.api;
import com.netbanking.payment.service.FundTransferService;
import com.netbanking.payment.service.BillPaymentService;
import com.netbanking.security.SecurityContextHelper;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1")
public class PaymentController {
    private final FundTransferService fundTransferService; private final BillPaymentService billPaymentService;
    public PaymentController(FundTransferService fundTransferService, BillPaymentService billPaymentService) { this.fundTransferService = fundTransferService; this.billPaymentService = billPaymentService; }
    @PostMapping("/transfers/otp-challenges") @ResponseStatus(HttpStatus.CREATED) public OtpChallengeResponse transferOtp(@Valid @RequestBody TransferOtpChallengeRequest request) { return fundTransferService.issueOtp(SecurityContextHelper.currentUserId(), request); }
    @PostMapping("/transfers") @ResponseStatus(HttpStatus.CREATED) public PaymentReceiptResponse transfer(@Valid @RequestBody FundTransferRequest request) { return fundTransferService.transfer(SecurityContextHelper.currentUserId(), request); }
    @PostMapping("/bill-payments/otp-challenges") @ResponseStatus(HttpStatus.CREATED) public OtpChallengeResponse billOtp(@Valid @RequestBody BillPaymentOtpChallengeRequest request) { return billPaymentService.issueOtp(SecurityContextHelper.currentUserId(), request); }
    @PostMapping("/bill-payments") @ResponseStatus(HttpStatus.CREATED) public PaymentReceiptResponse payBill(@Valid @RequestBody BillPaymentRequest request) { return billPaymentService.pay(SecurityContextHelper.currentUserId(), request); }
}
