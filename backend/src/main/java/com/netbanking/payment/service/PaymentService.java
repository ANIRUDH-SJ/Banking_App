package com.netbanking.payment.service;

import com.netbanking.account.domain.BankAccount;
import com.netbanking.account.repository.BankAccountRepository;
import com.netbanking.account.service.AccountService;
import com.netbanking.beneficiary.domain.Beneficiary;
import com.netbanking.beneficiary.service.BeneficiaryService;
import com.netbanking.biller.domain.Biller;
import com.netbanking.biller.service.BillerService;
import com.netbanking.common.exception.ResourceNotFoundException;
import com.netbanking.common.exception.ConflictException;
import com.netbanking.audit.service.AuditLogService;
import com.netbanking.otp.domain.OtpPurpose;
import com.netbanking.otp.service.OtpService;
import com.netbanking.payment.api.*;
import com.netbanking.payment.domain.BillPayment;
import com.netbanking.payment.domain.FundTransfer;
import com.netbanking.payment.repository.BillPaymentRepository;
import com.netbanking.payment.repository.FundTransferRepository;
import com.netbanking.transaction.domain.EntryType;
import com.netbanking.transaction.domain.TransactionStatus;
import com.netbanking.transaction.domain.TransactionType;
import com.netbanking.transaction.repository.BankTransactionRepository;
import com.netbanking.transaction.service.CreateTransactionCommand;
import com.netbanking.transaction.service.TransactionRecord;
import com.netbanking.transaction.service.TransactionService;
import com.netbanking.user.domain.AppUser;
import com.netbanking.user.repository.AppUserRepository;
import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

@Service @Transactional
public class PaymentService {
    private final AccountService accountService; private final BankAccountRepository accountRepository; private final BeneficiaryService beneficiaryService; private final BillerService billerService;
    private final OtpService otpService; private final AppUserRepository userRepository; private final BankTransactionRepository transactionRepository; private final TransactionService transactionService;
    private final FundTransferRepository transferRepository; private final BillPaymentRepository billPaymentRepository;
    private final AuditLogService auditLogService; private final ApplicationEventPublisher eventPublisher;
    private final BigDecimal maxTransferAmount;
    public PaymentService(AccountService accountService, BankAccountRepository accountRepository, BeneficiaryService beneficiaryService, BillerService billerService, OtpService otpService, AppUserRepository userRepository, BankTransactionRepository transactionRepository, TransactionService transactionService, FundTransferRepository transferRepository, BillPaymentRepository billPaymentRepository, AuditLogService auditLogService, ApplicationEventPublisher eventPublisher, @Value("${app.payments.transfer-max-amount:100000}") BigDecimal maxTransferAmount) {
        this.accountService = accountService; this.accountRepository = accountRepository; this.beneficiaryService = beneficiaryService; this.billerService = billerService; this.otpService = otpService; this.userRepository = userRepository; this.transactionRepository = transactionRepository; this.transactionService = transactionService; this.transferRepository = transferRepository; this.billPaymentRepository = billPaymentRepository; this.auditLogService = auditLogService; this.eventPublisher = eventPublisher; this.maxTransferAmount = maxTransferAmount;
    }
    public OtpChallengeResponse issueTransferOtp(Long userId, TransferOtpChallengeRequest request) {
        accountService.requireOwnership(userId, request.sourceAccountId());
        beneficiaryService.requireActiveOwned(userId, request.beneficiaryId());
        if (request.amount().compareTo(maxTransferAmount) > 0) throw new IllegalArgumentException("Transfer amount exceeds the configured limit.");
        return issueOtp(userId, OtpPurpose.FUND_TRANSFER, PaymentIntent.transferDigest(userId, request.sourceAccountId(), request.beneficiaryId(), request.amount()));
    }
    public OtpChallengeResponse issueBillPaymentOtp(Long userId, BillPaymentOtpChallengeRequest request) {
        accountService.requireOwnership(userId, request.sourceAccountId());
        Biller biller = billerService.requireActiveAndAmount(request.billerId(), request.amount());
        biller.validateReference(request.billReference());
        return issueOtp(userId, OtpPurpose.BILL_PAYMENT, PaymentIntent.billPaymentDigest(userId, request.sourceAccountId(), request.billerId(), request.amount(), request.billReference()));
    }
    public PaymentReceiptResponse transfer(Long userId, FundTransferRequest request) {
        String fingerprint = PaymentIntent.transferFingerprint(userId, request.sourceAccountId(), request.beneficiaryId(), request.amount(), request.narration());
        var prior = transferRepository.findByInitiatedByUserIdAndIdempotencyKey(userId, request.idempotencyKey()); if (prior.isPresent()) { if (!prior.get().matchesRequest(fingerprint)) throw new ConflictException("Idempotency key has already been used for a different transfer."); return transferReceipt(prior.get()); }
        accountService.requireOwnership(userId, request.sourceAccountId()); Beneficiary beneficiary = beneficiaryService.requireActiveOwned(userId, request.beneficiaryId());
        if (request.amount().compareTo(maxTransferAmount) > 0) throw new IllegalArgumentException("Transfer amount exceeds the configured limit.");
        otpService.verifyForUser(userId, request.otpChallengeId(), request.otpCode(), OtpPurpose.FUND_TRANSFER, PaymentIntent.transferDigest(userId, request.sourceAccountId(), request.beneficiaryId(), request.amount()));
        BankAccount account = lockedActiveAccount(request.sourceAccountId());
        if (account.getAccountNumber().equals(beneficiary.getAccountNumber())) throw new IllegalArgumentException("Source and destination accounts must be different.");
        TransactionRecord transaction = createAndCompleteTransaction(account, userId, beneficiary.getBeneficiaryId(), TransactionType.TRANSFER, request.amount(), blankToNull(request.narration()));
        FundTransfer transfer = transferRepository.save(new FundTransfer(transaction.transactionId(), account.getAccountId(), beneficiary.getBeneficiaryId(), userId, request.idempotencyKey(), fingerprint));
        auditLogService.record(userId, "FUND_TRANSFER", "BANK_TRANSACTION", transaction.reference(), "SUCCESS");
        eventPublisher.publishEvent(new PaymentCompletedEvent(userId, "Fund transfer", transaction.reference(), transaction.amount(), transaction.currencyCode()));
        return new PaymentReceiptResponse(transfer.getTransferId(), transaction.transactionId(), transaction.reference(), transfer.getTransferStatus(), transaction.amount(), transaction.currencyCode());
    }
    public PaymentReceiptResponse payBill(Long userId, BillPaymentRequest request) {
        String fingerprint = PaymentIntent.billPaymentFingerprint(userId, request.sourceAccountId(), request.billerId(), request.amount(), request.billReference());
        var prior = billPaymentRepository.findByInitiatedByUserIdAndIdempotencyKey(userId, request.idempotencyKey()); if (prior.isPresent()) { if (!prior.get().matchesRequest(fingerprint)) throw new ConflictException("Idempotency key has already been used for a different bill payment."); return billReceipt(prior.get()); }
        accountService.requireOwnership(userId, request.sourceAccountId()); Biller biller = billerService.requireActiveAndAmount(request.billerId(), request.amount()); biller.validateReference(request.billReference());
        otpService.verifyForUser(userId, request.otpChallengeId(), request.otpCode(), OtpPurpose.BILL_PAYMENT, PaymentIntent.billPaymentDigest(userId, request.sourceAccountId(), request.billerId(), request.amount(), request.billReference()));
        BankAccount account = lockedActiveAccount(request.sourceAccountId());
        TransactionRecord transaction = createAndCompleteTransaction(account, userId, null, TransactionType.WITHDRAWAL, request.amount(), "Bill payment: " + biller.getBillerCode());
        BillPayment payment = billPaymentRepository.save(new BillPayment(transaction.transactionId(), account.getAccountId(), biller.getBillerId(), userId, request.billReference().trim(), request.idempotencyKey(), fingerprint));
        auditLogService.record(userId, "BILL_PAYMENT", "BANK_TRANSACTION", transaction.reference(), "SUCCESS");
        eventPublisher.publishEvent(new PaymentCompletedEvent(userId, "Bill payment", transaction.reference(), transaction.amount(), transaction.currencyCode()));
        return new PaymentReceiptResponse(payment.getBillPaymentId(), transaction.transactionId(), transaction.reference(), payment.getPaymentStatus(), transaction.amount(), transaction.currencyCode());
    }
    private OtpChallengeResponse issueOtp(Long userId, OtpPurpose purpose, String intentDigest) { AppUser user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User was not found.")); return new OtpChallengeResponse(otpService.issue(user, purpose, intentDigest).challengeId(), "OTP_SENT"); }
    private BankAccount lockedActiveAccount(Long id) { return accountRepository.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Account was not found.")); }
    private TransactionRecord createAndCompleteTransaction(BankAccount account, Long userId, Long beneficiaryId, TransactionType type, BigDecimal amount, String narration) {
        TransactionRecord transaction = transactionService.createTransaction(new CreateTransactionCommand(account.getAccountId(), null, beneficiaryId, userId, type, amount, account.getCurrencyCode(), narration));
        transactionService.changeStatus(transaction.transactionId(), TransactionStatus.PROCESSING, userId, null);
        account.debit(amount);
        transactionService.postEntry(transaction.transactionId(), account.getAccountId(), EntryType.DEBIT, account.getCurrentBalance());
        return transactionService.changeStatus(transaction.transactionId(), TransactionStatus.COMPLETED, userId, null);
    }
    private PaymentReceiptResponse transferReceipt(FundTransfer p) { var t = transactionRepository.findById(p.getTransactionId()).orElseThrow(() -> new ResourceNotFoundException("Transaction was not found.")); return new PaymentReceiptResponse(p.getTransferId(), t.getTransactionId(), t.getTransactionReference(), p.getTransferStatus(), t.getAmount(), t.getCurrencyCode().trim()); }
    private PaymentReceiptResponse billReceipt(BillPayment p) { var t = transactionRepository.findById(p.getTransactionId()).orElseThrow(() -> new ResourceNotFoundException("Transaction was not found.")); return new PaymentReceiptResponse(p.getBillPaymentId(), t.getTransactionId(), t.getTransactionReference(), p.getPaymentStatus(), t.getAmount(), t.getCurrencyCode().trim()); }
    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
