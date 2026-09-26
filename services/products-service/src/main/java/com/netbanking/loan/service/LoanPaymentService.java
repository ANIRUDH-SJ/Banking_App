package com.netbanking.loan.service;

import com.netbanking.common.exception.*;
import com.netbanking.contracts.LedgerReceipt;
import com.netbanking.discovery.*;
import com.netbanking.loan.api.*;
import com.netbanking.loan.domain.LoanPayment;
import com.netbanking.loan.repository.*;

import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class LoanPaymentService {
    private final LoanRepository loans;
    private final LoanPaymentRepository payments;
    private final CustomerDirectory customers;
    private final LedgerClient ledger;
    private final LoanRepaymentStore store;

    public LoanPaymentService(
            LoanRepository loans,
            LoanPaymentRepository payments,
            CustomerDirectory customers,
            LedgerClient ledger,
            LoanRepaymentStore store) {
        this.loans = loans;
        this.payments = payments;
        this.customers = customers;
        this.ledger = ledger;
        this.store = store;
    }

    public LoanPaymentResponse pay(Long userId, Long loanId, CreateLoanPaymentRequest request) {
        Long customerId = customers.requireCustomerIdForUser(userId);
        if (loans.findByLoanIdAndCustomerId(loanId, customerId).isEmpty())
            throw new ResourceNotFoundException("Loan was not found.");
        var operation = store.find(loanId, request.idempotencyKey());
        String fingerprint =
                LoanPaymentIntent.fingerprint(
                        userId, loanId, request.sourceAccountId(), request.amount());
        if (operation == null)
            operation =
                    store.prepare(
                            userId,
                            customerId,
                            loanId,
                            request,
                            ledger.account(userId, request.sourceAccountId()));
        if (!operation.fingerprint().equals(fingerprint))
            throw new ConflictException(
                    "Idempotency key was used for different repayment details.");
        if ("COMPLETED".equals(operation.state())) return store.receipt(operation);
        if ("FAILED".equals(operation.state()))
            throw new ConflictException("Repayment failed. Correct the request and use a new key.");
        return settle(operation);
    }

    public LoanPaymentResponse settle(LoanRepaymentStore.Operation operation) {
        LedgerReceipt receipt;
        try {
            receipt = ledger.post(operation.command());
        } catch (ResponseStatusException rejected) {
            if (java.util.Set.of(400, 403, 404, 409, 422)
                    .contains(rejected.getStatusCode().value())) store.failed(operation);
            throw rejected;
        }
        return store.complete(operation, receipt);
    }

    public Page<LoanPaymentResponse> getPayments(Long userId, Long loanId, int page, int size) {
        if (page < 0 || size < 1 || size > 100)
            throw new IllegalArgumentException(
                    "Page must be non-negative and size between 1 and 100.");
        Long customerId = customers.requireCustomerIdForUser(userId);
        if (loans.findByLoanIdAndCustomerId(loanId, customerId).isEmpty())
            throw new ResourceNotFoundException("Loan was not found.");
        return payments.findByLoanId(
                        loanId,
                        PageRequest.of(
                                page,
                                size,
                                Sort.by(
                                        Sort.Order.desc("paidAt"),
                                        Sort.Order.desc("loanPaymentId"))))
                .map(LoanPaymentService::toResponse);
    }

    static LoanPaymentResponse toResponse(LoanPayment payment) {
        return new LoanPaymentResponse(
                payment.getLoanPaymentId(),
                payment.getLoanId(),
                payment.getSourceAccountId(),
                payment.getTransactionId(),
                payment.getTransactionReference(),
                payment.getAmount(),
                payment.getCurrencyCode().trim(),
                payment.getOutstandingAfter(),
                payment.getPaymentStatus().name(),
                payment.getPaidAt());
    }
}
