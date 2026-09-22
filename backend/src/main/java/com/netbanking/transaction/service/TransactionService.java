package com.netbanking.transaction.service;

import com.netbanking.account.service.AccountService;
import com.netbanking.common.exception.ResourceNotFoundException;
import com.netbanking.transaction.api.TransactionResponse;
import com.netbanking.transaction.domain.AccountTransactionEntry;
import com.netbanking.transaction.domain.BankTransaction;
import com.netbanking.transaction.repository.AccountTransactionEntryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TransactionService {

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("postedAt"), Sort.Order.desc("entryId"));

    private final AccountService accountService;
    private final AccountTransactionEntryRepository entryRepository;

    public TransactionService(AccountService accountService, AccountTransactionEntryRepository entryRepository) {
        this.accountService = accountService;
        this.entryRepository = entryRepository;
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

    private static void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("Page must be non-negative and size must be between 1 and 100.");
        }
    }

    public static TransactionResponse toResponse(AccountTransactionEntry entry) {
        BankTransaction transaction = entry.getTransaction();
        return new TransactionResponse(
                entry.getEntryId(), transaction.getTransactionId(), transaction.getTransactionReference(),
                transaction.getTransactionType(), transaction.getTransactionStatus(), entry.getEntryType(),
                entry.getAmount(), transaction.getCurrencyCode().trim(), entry.getBalanceAfter(),
                transaction.getNarration(), entry.getPostedAt());
    }
}
