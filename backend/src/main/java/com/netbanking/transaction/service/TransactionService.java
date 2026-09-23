package com.netbanking.transaction.service;

import com.netbanking.account.service.AccountService;
import com.netbanking.common.exception.ConflictException;
import com.netbanking.common.exception.ResourceNotFoundException;
import com.netbanking.transaction.api.TransactionResponse;
import com.netbanking.transaction.api.TransactionStatusHistoryResponse;
import com.netbanking.transaction.domain.AccountTransactionEntry;
import com.netbanking.transaction.domain.BankTransaction;
import com.netbanking.transaction.domain.EntryType;
import com.netbanking.transaction.domain.TransactionStatus;
import com.netbanking.transaction.domain.TransactionStatusHistory;
import com.netbanking.transaction.domain.TransactionType;
import com.netbanking.transaction.repository.AccountTransactionEntryRepository;
import com.netbanking.transaction.repository.BankTransactionRepository;
import com.netbanking.transaction.repository.TransactionStatusHistoryRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TransactionService {

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("postedAt"), Sort.Order.desc("entryId"));
    private static final int REFERENCE_ATTEMPTS = 5;

    private final AccountService accountService;
    private final BankTransactionRepository transactionRepository;
    private final AccountTransactionEntryRepository entryRepository;
    private final TransactionStatusHistoryRepository statusHistoryRepository;
    private final TransactionReferenceGenerator referenceGenerator;

    public TransactionService(AccountService accountService,
                              BankTransactionRepository transactionRepository,
                              AccountTransactionEntryRepository entryRepository,
                              TransactionStatusHistoryRepository statusHistoryRepository,
                              TransactionReferenceGenerator referenceGenerator) {
        this.accountService = accountService;
        this.transactionRepository = transactionRepository;
        this.entryRepository = entryRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.referenceGenerator = referenceGenerator;
    }

    public Page<TransactionResponse> getAccountTransactions(Long userId, Long accountId, int page, int size) {
        validatePage(page, size);
        accountService.requireOwnership(userId, accountId);
        return entryRepository.findByAccountId(accountId, PageRequest.of(page, size, NEWEST_FIRST))
                .map(TransactionService::toResponse);
    }

    public TransactionResponse getAccountTransaction(Long userId, Long accountId, Long entryId) {
        accountService.requireOwnership(userId, accountId);
        AccountTransactionEntry entry = entryRepository.findByEntryIdAndAccountId(entryId, accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction entry was not found."));
        return toResponse(entry);
    }

    public List<TransactionStatusHistoryResponse> getStatusHistory(
            Long userId, Long accountId, Long entryId) {
        accountService.requireOwnership(userId, accountId);
        AccountTransactionEntry entry = entryRepository.findByEntryIdAndAccountId(entryId, accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction entry was not found."));
        return statusHistoryRepository
                .findByTransactionTransactionIdOrderByChangedAtAscStatusHistoryIdAsc(
                        entry.getTransaction().getTransactionId())
                .stream()
                .map(TransactionService::toHistoryResponse)
                .toList();
    }

    /**
     * Creates a pending transaction for a money-movement service. This method does not change balances.
     */
    @Transactional
    public TransactionRecord createTransaction(CreateTransactionCommand command) {
        validateCommand(command);
        String reference = nextUniqueReference();
        String currencyCode = command.currencyCode().strip().toUpperCase(Locale.ROOT);
        String narration = command.narration() == null ? null : command.narration().strip();
        BankTransaction transaction = transactionRepository.save(new BankTransaction(
                reference, command.debitAccountId(), command.creditAccountId(), command.beneficiaryId(),
                command.initiatedByUserId(), command.type(), command.amount(), currencyCode, narration));
        statusHistoryRepository.save(new TransactionStatusHistory(
                transaction, null, TransactionStatus.PENDING, null, command.initiatedByUserId()));
        return toRecord(transaction);
    }

    /**
     * Records the account-specific debit or credit after the caller has atomically updated the balance.
     */
    @Transactional
    public TransactionResponse postEntry(Long transactionId, Long accountId, EntryType entryType,
                                         BigDecimal balanceAfter) {
        BankTransaction transaction = findTransaction(transactionId);
        if (transaction.getTransactionStatus() != TransactionStatus.PROCESSING) {
            throw new IllegalStateException("Ledger entries can be posted only while a transaction is processing.");
        }
        validatePostedAccount(transaction, accountId, entryType);
        validateMoney(balanceAfter, "Balance after posting", true);
        if (entryRepository.existsByTransactionTransactionIdAndAccountIdAndEntryType(
                transactionId, accountId, entryType)) {
            throw new ConflictException("This ledger entry has already been posted.");
        }
        AccountTransactionEntry entry = entryRepository.save(new AccountTransactionEntry(
                accountId, transaction, entryType, transaction.getAmount(), balanceAfter));
        return toResponse(entry);
    }

    @Transactional
    public TransactionRecord changeStatus(Long transactionId, TransactionStatus newStatus,
                                          Long changedByUserId, String failureReason) {
        if (newStatus == null) {
            throw new IllegalArgumentException("A transaction status is required.");
        }
        if (failureReason != null && failureReason.length() > 500) {
            throw new IllegalArgumentException("Failure reason must not exceed 500 characters.");
        }
        BankTransaction transaction = findTransaction(transactionId);
        TransactionStatus previousStatus = transaction.getTransactionStatus();
        if (newStatus == TransactionStatus.COMPLETED && previousStatus == TransactionStatus.PROCESSING) {
            requirePostedEntries(transaction);
        }
        transaction.transitionTo(newStatus, failureReason);
        statusHistoryRepository.save(new TransactionStatusHistory(
                transaction, previousStatus, newStatus, transaction.getFailureReason(), changedByUserId));
        return toRecord(transaction);
    }

    public TransactionRecord getByReference(String reference) {
        if (reference == null || reference.isBlank()) {
            throw new IllegalArgumentException("A transaction reference is required.");
        }
        return transactionRepository.findByTransactionReference(reference.strip())
                .map(TransactionService::toRecord)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction was not found."));
    }

    private static void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("Page must be non-negative and size must be between 1 and 100.");
        }
    }

    public static TransactionResponse toResponse(AccountTransactionEntry entry) {
        BankTransaction transaction = entry.getTransaction();
        return new TransactionResponse(
                entry.getEntryId(), transaction.getTransactionId(), transaction.getTransactionReference(),
                transaction.getTransactionType().name(), transaction.getTransactionStatus().name(),
                entry.getEntryType().name(),
                entry.getAmount(), transaction.getCurrencyCode().trim(), entry.getBalanceAfter(),
                transaction.getNarration(), entry.getPostedAt());
    }

    private String nextUniqueReference() {
        for (int attempt = 0; attempt < REFERENCE_ATTEMPTS; attempt++) {
            String reference = referenceGenerator.generate();
            if (!transactionRepository.existsByTransactionReference(reference)) {
                return reference;
            }
        }
        throw new IllegalStateException("A unique transaction reference could not be generated.");
    }

    private BankTransaction findTransaction(Long transactionId) {
        return transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction was not found."));
    }

    private static void validateCommand(CreateTransactionCommand command) {
        if (command == null || command.type() == null || command.initiatedByUserId() == null) {
            throw new IllegalArgumentException("Transaction type and initiating user are required.");
        }
        validateMoney(command.amount(), "Transaction amount", false);
        if (command.currencyCode() == null
                || !command.currencyCode().strip().matches("[A-Za-z]{3}")) {
            throw new IllegalArgumentException("Currency code must contain three letters.");
        }
        if (command.narration() != null && command.narration().length() > 500) {
            throw new IllegalArgumentException("Narration must not exceed 500 characters.");
        }
        if (command.debitAccountId() != null && command.debitAccountId().equals(command.creditAccountId())) {
            throw new IllegalArgumentException("Debit and credit accounts must be different.");
        }
        validateAccountsForType(command);
    }

    private static void validateAccountsForType(CreateTransactionCommand command) {
        boolean hasDebit = command.debitAccountId() != null;
        boolean hasCredit = command.creditAccountId() != null;
        boolean valid = switch (command.type()) {
            case TRANSFER -> hasDebit && hasCredit;
            case DEPOSIT -> !hasDebit && hasCredit;
            case WITHDRAWAL -> hasDebit && !hasCredit;
            case REVERSAL -> hasDebit || hasCredit;
        };
        if (!valid) {
            throw new IllegalArgumentException("Debit and credit accounts do not match the transaction type.");
        }
    }

    private static void validatePostedAccount(BankTransaction transaction, Long accountId, EntryType entryType) {
        if (accountId == null || entryType == null) {
            throw new IllegalArgumentException("Account and entry type are required.");
        }
        Long expectedAccount = entryType == EntryType.DEBIT
                ? transaction.getDebitAccountId() : transaction.getCreditAccountId();
        if (!accountId.equals(expectedAccount)) {
            throw new IllegalArgumentException("Ledger entry does not match the transaction account.");
        }
    }

    private void requirePostedEntries(BankTransaction transaction) {
        if (transaction.getDebitAccountId() != null
                && !entryRepository.existsByTransactionTransactionIdAndAccountIdAndEntryType(
                        transaction.getTransactionId(), transaction.getDebitAccountId(), EntryType.DEBIT)) {
            throw new IllegalStateException("The required debit ledger entry has not been posted.");
        }
        if (transaction.getCreditAccountId() != null
                && !entryRepository.existsByTransactionTransactionIdAndAccountIdAndEntryType(
                        transaction.getTransactionId(), transaction.getCreditAccountId(), EntryType.CREDIT)) {
            throw new IllegalStateException("The required credit ledger entry has not been posted.");
        }
    }

    private static void validateMoney(BigDecimal value, String field, boolean allowZero) {
        if (value == null || (allowZero ? value.signum() < 0 : value.signum() <= 0)) {
            throw new IllegalArgumentException(field + (allowZero ? " must not be negative." : " must be positive."));
        }
        BigDecimal normalized = value.stripTrailingZeros();
        if (Math.max(normalized.scale(), 0) > 4 || normalized.precision() - normalized.scale() > 15) {
            throw new IllegalArgumentException(field + " exceeds the supported precision.");
        }
    }

    private static TransactionRecord toRecord(BankTransaction transaction) {
        return new TransactionRecord(
                transaction.getTransactionId(), transaction.getTransactionReference(),
                transaction.getTransactionType(), transaction.getTransactionStatus(), transaction.getAmount(),
                transaction.getCurrencyCode().trim());
    }

    private static TransactionStatusHistoryResponse toHistoryResponse(TransactionStatusHistory history) {
        return new TransactionStatusHistoryResponse(
                history.getStatusHistoryId(),
                history.getPreviousStatus() == null ? null : history.getPreviousStatus().name(),
                history.getNewStatus().name(), history.getFailureReason(), history.getChangedByUserId(),
                history.getChangedAt());
    }
}
