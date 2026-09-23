package com.netbanking.loan.service;

import com.netbanking.account.domain.BankAccount;
import com.netbanking.account.repository.BankAccountRepository;
import com.netbanking.account.service.AccountService;
import com.netbanking.common.exception.ResourceNotFoundException;
import com.netbanking.customer.service.CustomerService;
import com.netbanking.loan.api.CreateLoanPaymentRequest;
import com.netbanking.loan.api.LoanPaymentResponse;
import com.netbanking.loan.domain.Loan;
import com.netbanking.loan.domain.LoanPayment;
import com.netbanking.loan.repository.LoanPaymentRepository;
import com.netbanking.loan.repository.LoanRepository;
import com.netbanking.transaction.domain.EntryType;
import com.netbanking.transaction.domain.TransactionStatus;
import com.netbanking.transaction.domain.TransactionType;
import com.netbanking.transaction.service.CreateTransactionCommand;
import com.netbanking.transaction.service.TransactionRecord;
import com.netbanking.transaction.service.TransactionService;
import java.util.Locale;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class LoanPaymentService {

    private static final Sort NEWEST_FIRST = Sort.by(
            Sort.Order.desc("paidAt"), Sort.Order.desc("loanPaymentId"));

    private final LoanRepository loanRepository;
    private final LoanPaymentRepository paymentRepository;
    private final BankAccountRepository accountRepository;
    private final CustomerService customerService;
    private final AccountService accountService;
    private final TransactionService transactionService;
    private final LoanPaymentAuditWriter auditWriter;

    public LoanPaymentService(LoanRepository loanRepository,
                              LoanPaymentRepository paymentRepository,
                              BankAccountRepository accountRepository,
                              CustomerService customerService,
                              AccountService accountService,
                              TransactionService transactionService,
                              LoanPaymentAuditWriter auditWriter) {
        this.loanRepository = loanRepository;
        this.paymentRepository = paymentRepository;
        this.accountRepository = accountRepository;
        this.customerService = customerService;
        this.accountService = accountService;
        this.transactionService = transactionService;
        this.auditWriter = auditWriter;
    }

    @Transactional
    public LoanPaymentResponse pay(Long userId, Long loanId, CreateLoanPaymentRequest request) {
        Long customerId = customerService.requireCustomerIdForUser(userId);
        Loan loan = loanRepository.findByLoanIdAndCustomerIdForUpdate(loanId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Loan was not found."));

        String idempotencyKey = request.idempotencyKey().strip();
        var existing = paymentRepository.findByLoanIdAndIdempotencyKey(loanId, idempotencyKey);
        if (existing.isPresent()) {
            return toResponse(existing.get());
        }

        accountService.requireOwnership(userId, request.sourceAccountId());
        BankAccount account = accountRepository.findByIdForUpdate(request.sourceAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Source account was not found."));
        String currencyCode = account.getCurrencyCode().trim().toUpperCase(Locale.ROOT);
        if (!currencyCode.equals(loan.getCurrencyCode().trim().toUpperCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Source account and loan currencies must match.");
        }

        loan.applyPayment(request.amount());
        account.debit(request.amount());

        TransactionRecord transaction = transactionService.createTransaction(new CreateTransactionCommand(
                account.getAccountId(), null, null, userId, TransactionType.LOAN_PAYMENT,
                request.amount(), currencyCode, "Loan payment " + loan.getLoanAccountNumber()));
        transactionService.changeStatus(
                transaction.transactionId(), TransactionStatus.PROCESSING, userId, null);
        transactionService.postEntry(
                transaction.transactionId(), account.getAccountId(), EntryType.DEBIT,
                account.getCurrentBalance());

        LoanPayment payment = paymentRepository.save(new LoanPayment(
                loanId, account.getAccountId(), transaction.transactionId(),
                transaction.reference(), idempotencyKey, request.amount(),
                currencyCode, loan.getOutstandingPrincipal()));
        transactionService.changeStatus(
                transaction.transactionId(), TransactionStatus.COMPLETED, userId, null);
        auditWriter.recordCompleted(userId, loanId, transaction.reference());
        return toResponse(payment);
    }

    public Page<LoanPaymentResponse> getPayments(
            Long userId, Long loanId, int page, int size) {
        validatePage(page, size);
        Long customerId = customerService.requireCustomerIdForUser(userId);
        if (loanRepository.findByLoanIdAndCustomerId(loanId, customerId).isEmpty()) {
            throw new ResourceNotFoundException("Loan was not found.");
        }
        return paymentRepository.findByLoanId(loanId, PageRequest.of(page, size, NEWEST_FIRST))
                .map(LoanPaymentService::toResponse);
    }

    private static void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException(
                    "Page must be non-negative and size must be between 1 and 100.");
        }
    }

    private static LoanPaymentResponse toResponse(LoanPayment payment) {
        return new LoanPaymentResponse(
                payment.getLoanPaymentId(), payment.getLoanId(), payment.getSourceAccountId(),
                payment.getTransactionId(), payment.getTransactionReference(), payment.getAmount(),
                payment.getCurrencyCode().trim(), payment.getOutstandingAfter(),
                payment.getPaymentStatus().name(), payment.getPaidAt());
    }
}
