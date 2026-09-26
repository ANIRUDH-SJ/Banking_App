package com.netbanking.loan.api;

import com.netbanking.loan.service.LoanPaymentService;
import com.netbanking.security.SecurityContextHelper;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/loans/{loanId}/payments")
public class LoanPaymentController {

    private final LoanPaymentService paymentService;

    public LoanPaymentController(LoanPaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LoanPaymentResponse pay(
            @PathVariable Long loanId, @Valid @RequestBody CreateLoanPaymentRequest request) {
        return paymentService.pay(SecurityContextHelper.currentUserId(), loanId, request);
    }

    @GetMapping
    public Page<LoanPaymentResponse> getPayments(
            @PathVariable Long loanId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return paymentService.getPayments(
                SecurityContextHelper.currentUserId(), loanId, page, size);
    }
}
