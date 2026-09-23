package com.netbanking.payment.service;

import com.netbanking.account.domain.BankAccount;
import com.netbanking.account.repository.BankAccountRepository;
import com.netbanking.account.service.AccountService;
import com.netbanking.beneficiary.domain.Beneficiary;
import com.netbanking.beneficiary.service.BeneficiaryService;
import com.netbanking.biller.domain.Biller;
import com.netbanking.biller.service.BillerService;
import com.netbanking.common.exception.ResourceNotFoundException;
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
import org.springframework.transaction.annotation.Transactional;

@Service @Transactional
public class PaymentService {
    private final AccountService accountService; private final BankAccountRepository accountRepository; private final BeneficiaryService beneficiaryService; private final BillerService billerService;
    private final OtpService otpService; private final AppUserRepository userRepository; private final BankTransactionRepository transactionRepository; private final TransactionService transactionService;
    private final FundTransferRepository transferRepository; private final BillPaymentRepository billPaymentRepository;
    private final BigDecimal maxTransferAmount;
    public PaymentService(AccountService accountService, BankAccountRepository accountRepository, BeneficiaryService beneficiaryService, BillerService billerService, OtpService otpService, AppUserRepository userRepository, BankTransactionRepository transactionRepository, TransactionService transactionService, FundTransferRepository transferRepository, BillPaymentRepository billPaymentRepository, @Value("${app.payments.transfer-max-amount:100000}") BigDecimal maxTransferAmount) {
        this.accountService = accountService; this.accountRepository = accountRepository; this.beneficiaryService = beneficiaryService; this.billerService = billerService; this.otpService = otpService; this.userRepository = userRepository; this.transactionRepository = transactionRepository; this.transactionService = transactionService; this.transferRepository = transferRepository; this.billPaymentRepository = billPaymentRepository; this.maxTransferAmount = maxTransferAmount;
    }
    public OtpChallengeResponse issueTransferOtp(Long userId, OtpChallengeRequest request) { return issueOtp(userId, request.sourceAccountId(), OtpPurpose.FUND_TRANSFER); }
    public OtpChallengeResponse issueBillPaymentOtp(Long userId, OtpChallengeRequest request) { return issueOtp(userId, request.sourceAccountId(), OtpPurpose.BILL_PAYMENT); }
    public PaymentReceiptResponse transfer(Long userId, FundTransferRequest request) {
        var prior = transferRepository.findByInitiatedByUserIdAndIdempotencyKey(userId, request.idempotencyKey()); if (prior.isPresent()) return transferReceipt(prior.get());
        accountService.requireOwnership(userId, request.sourceAccountId()); Beneficiary beneficiary = beneficiaryService.requireActiveOwned(userId, request.beneficiaryId());
        if (request.amount().compareTo(maxTransferAmount) > 0) throw new IllegalArgumentException("Transfer amount exceeds the configured limit.");
        otpService.verifyForUser(userId, request.otpChallengeId(), request.otpCode(), OtpPurpose.FUND_TRANSFER);
        BankAccount account = lockedActiveAccount(request.sourceAccountId());
        if (account.getAccountNumber().equals(beneficiary.getAccountNumber())) throw new IllegalArgumentException("Source and destination accounts must be different.");
        TransactionRecord transaction = createAndCompleteTransaction(account, userId, beneficiary.getBeneficiaryId(), TransactionType.TRANSFER, request.amount(), blankToNull(request.narration()));
        FundTransfer transfer = transferRepository.save(new FundTransfer(transaction.transactionId(), account.getAccountId(), beneficiary.getBeneficiaryId(), userId, request.idempotencyKey()));
        return new PaymentReceiptResponse(transfer.getTransferId(), transaction.transactionId(), transaction.reference(), transfer.getTransferStatus(), transaction.amount(), transaction.currencyCode());
    }
    public PaymentReceiptResponse payBill(Long userId, BillPaymentRequest request) {
        var prior = billPaymentRepository.findByInitiatedByUserIdAndIdempotencyKey(userId, request.idempotencyKey()); if (prior.isPresent()) return billReceipt(prior.get());
        accountService.requireOwnership(userId, request.sourceAccountId()); Biller biller = billerService.requireActiveAndAmount(request.billerId(), request.amount());
        otpService.verifyForUser(userId, request.otpChallengeId(), request.otpCode(), OtpPurpose.BILL_PAYMENT);
        BankAccount account = lockedActiveAccount(request.sourceAccountId());
        TransactionRecord transaction = createAndCompleteTransaction(account, userId, null, TransactionType.WITHDRAWAL, request.amount(), "Bill payment: " + biller.getBillerCode());
        BillPayment payment = billPaymentRepository.save(new BillPayment(transaction.transactionId(), account.getAccountId(), biller.getBillerId(), userId, request.billReference().trim(), request.idempotencyKey()));
        return new PaymentReceiptResponse(payment.getBillPaymentId(), transaction.transactionId(), transaction.reference(), payment.getPaymentStatus(), transaction.amount(), transaction.currencyCode());
    }
    private OtpChallengeResponse issueOtp(Long userId, Long accountId, OtpPurpose purpose) { accountService.requireOwnership(userId, accountId); AppUser user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User was not found.")); return new OtpChallengeResponse(otpService.issue(user, purpose).challengeId(), "OTP_SENT"); }
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
