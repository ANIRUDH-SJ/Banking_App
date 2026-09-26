package com.netbanking.payment.service;

import com.netbanking.beneficiary.service.BeneficiaryService;
import com.netbanking.biller.service.BillerService;
import com.netbanking.common.exception.ConflictException;
import com.netbanking.contracts.*;
import com.netbanking.discovery.LedgerClient;
import com.netbanking.discovery.OtpClient;
import com.netbanking.payment.api.*;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class PaymentService {
    private final BeneficiaryService beneficiaries;
    private final BillerService billers;
    private final LedgerClient ledger;
    private final OtpClient otp;
    private final PaymentWorkflowStore store;
    private final BigDecimal limit;
    private final PaymentAuditService audit;

    public PaymentService(
            BeneficiaryService beneficiaries,
            BillerService billers,
            LedgerClient ledger,
            OtpClient otp,
            PaymentWorkflowStore store,
            PaymentAuditService audit,
            @Value("${app.payments.transfer-max-amount:100000}") BigDecimal limit) {
        this.beneficiaries = beneficiaries;
        this.billers = billers;
        this.ledger = ledger;
        this.otp = otp;
        this.store = store;
        this.audit = audit;
        this.limit = limit;
    }

    public OtpChallengeResponse issueTransferOtp(Long userId, TransferOtpChallengeRequest r) {
        var account = eligibleAccount(userId, r.sourceAccountId());
        var beneficiary = beneficiaries.requireActiveOwned(userId, r.beneficiaryId());
        if (account.accountNumber().equals(beneficiary.getAccountNumber()))
            throw new IllegalArgumentException("Source and destination must differ.");
        requireLimit(r.amount());
        return new OtpChallengeResponse(
                otp.issue(
                                userId,
                                "FUND_TRANSFER",
                                PaymentIntent.transferDigest(
                                        userId, r.sourceAccountId(), r.beneficiaryId(), r.amount()))
                        .challengeId(),
                "OTP_SENT");
    }

    public OtpChallengeResponse issueBillPaymentOtp(Long userId, BillPaymentOtpChallengeRequest r) {
        eligibleAccount(userId, r.sourceAccountId());
        billers.requireActiveAndAmount(r.billerId(), r.amount())
                .validateReference(r.billReference());
        return new OtpChallengeResponse(
                otp.issue(
                                userId,
                                "BILL_PAYMENT",
                                PaymentIntent.billPaymentDigest(
                                        userId,
                                        r.sourceAccountId(),
                                        r.billerId(),
                                        r.amount(),
                                        r.billReference()))
                        .challengeId(),
                "OTP_SENT");
    }

    public PaymentReceiptResponse transfer(Long userId, FundTransferRequest r) {
        try {
            return transferRequest(userId, r);
        } catch (RuntimeException failure) {
            audit.recordRejected(userId, r.sourceAccountId(), "FUND_TRANSFER", failure);
            throw failure;
        }
    }

    private PaymentReceiptResponse transferRequest(Long userId, FundTransferRequest r) {
        String fingerprint =
                PaymentIntent.transferFingerprint(
                        userId, r.sourceAccountId(), r.beneficiaryId(), r.amount(), r.narration());
        var operation = store.find(userId, "TRANSFER", r.idempotencyKey());
        if (operation == null) {
            var account = eligibleAccount(userId, r.sourceAccountId());
            var beneficiary = beneficiaries.requireActiveOwned(userId, r.beneficiaryId());
            requireLimit(r.amount());
            if (account.accountNumber().equals(beneficiary.getAccountNumber()))
                throw new IllegalArgumentException("Source and destination must differ.");
            var command =
                    new LedgerCommand(
                            UUID.randomUUID().toString(),
                            userId,
                            r.sourceAccountId(),
                            beneficiary.getAccountNumber(),
                            beneficiary.getIfscCode(),
                            "TRANSFER",
                            r.amount(),
                            account.currencyCode(),
                            PaymentIntent.normalize(r.narration()));
            operation =
                    create(
                            "TRANSFER",
                            r.idempotencyKey(),
                            fingerprint,
                            PaymentIntent.transferDigest(
                                    userId, r.sourceAccountId(), r.beneficiaryId(), r.amount()),
                            command,
                            new PaymentWorkflowStore.Details(
                                    r.beneficiaryId(),
                                    null,
                                    null,
                                    PaymentIntent.normalize(r.narration())));
        }
        return authorizeAndComplete(
                operation, fingerprint, r.otpChallengeId(), r.otpCode(), "FUND_TRANSFER");
    }

    public PaymentReceiptResponse payBill(Long userId, BillPaymentRequest r) {
        try {
            return payBillRequest(userId, r);
        } catch (RuntimeException failure) {
            audit.recordRejected(userId, r.sourceAccountId(), "BILL_PAYMENT", failure);
            throw failure;
        }
    }

    private PaymentReceiptResponse payBillRequest(Long userId, BillPaymentRequest r) {
        String fingerprint =
                PaymentIntent.billPaymentFingerprint(
                        userId, r.sourceAccountId(), r.billerId(), r.amount(), r.billReference());
        var operation = store.find(userId, "BILL_PAYMENT", r.idempotencyKey());
        if (operation == null) {
            var account = eligibleAccount(userId, r.sourceAccountId());
            var biller = billers.requireActiveAndAmount(r.billerId(), r.amount());
            biller.validateReference(r.billReference());
            var command =
                    new LedgerCommand(
                            UUID.randomUUID().toString(),
                            userId,
                            r.sourceAccountId(),
                            null,
                            null,
                            "WITHDRAWAL",
                            r.amount(),
                            account.currencyCode(),
                            "Bill payment: " + biller.getBillerCode());
            operation =
                    create(
                            "BILL_PAYMENT",
                            r.idempotencyKey(),
                            fingerprint,
                            PaymentIntent.billPaymentDigest(
                                    userId,
                                    r.sourceAccountId(),
                                    r.billerId(),
                                    r.amount(),
                                    r.billReference()),
                            command,
                            new PaymentWorkflowStore.Details(
                                    null, r.billerId(), r.billReference().strip(), null));
        }
        return authorizeAndComplete(
                operation, fingerprint, r.otpChallengeId(), r.otpCode(), "BILL_PAYMENT");
    }

    private PaymentWorkflowStore.Operation create(
            String kind,
            String requestKey,
            String fingerprint,
            String digest,
            LedgerCommand command,
            PaymentWorkflowStore.Details details) {
        try {
            return store.create(kind, requestKey, fingerprint, digest, command, details);
        } catch (DataIntegrityViolationException concurrent) {
            var prior = store.find(command.userId(), kind, requestKey);
            if (prior == null) throw concurrent;
            return prior;
        }
    }

    private PaymentReceiptResponse authorizeAndComplete(
            PaymentWorkflowStore.Operation operation,
            String fingerprint,
            String challenge,
            String code,
            String purpose) {
        if (!operation.fingerprint().equals(fingerprint))
            throw new ConflictException("Idempotency key was used for different payment details.");
        if ("COMPLETED".equals(operation.state())) return operation.receipt();
        if ("FAILED".equals(operation.state()))
            throw new ConflictException(
                    "Payment failed. Use a new request key after correcting the request.");
        if ("AWAITING_OTP".equals(operation.state())) {
            otp.authorize(
                    operation.key(),
                    operation.command().userId(),
                    challenge,
                    code,
                    purpose,
                    operation.digest());
            store.authorized(operation.key());
        }
        return settle(store.get(operation.key()));
    }

    public PaymentReceiptResponse settle(PaymentWorkflowStore.Operation operation) {
        LedgerReceipt receipt;
        try {
            receipt = ledger.post(operation.command());
        } catch (ResponseStatusException rejected) {
            if (java.util.Set.of(400, 403, 404, 409, 422)
                    .contains(rejected.getStatusCode().value())) store.failed(operation.key());
            throw rejected;
        }
        return store.complete(operation.key(), receipt);
    }

    private AccountSnapshot eligibleAccount(Long userId, Long accountId) {
        var account = ledger.account(userId, accountId);
        if (!"ACTIVE".equals(account.status()))
            throw new ConflictException("Source account must be active.");
        return account;
    }

    private void requireLimit(BigDecimal amount) {
        if (amount.compareTo(limit) > 0)
            throw new IllegalArgumentException("Transfer amount exceeds the configured limit.");
    }
}
