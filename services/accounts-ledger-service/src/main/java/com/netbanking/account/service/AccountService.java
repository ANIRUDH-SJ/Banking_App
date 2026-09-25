package com.netbanking.account.service;

import com.netbanking.account.api.AccountStatus;
import com.netbanking.account.api.AccountSummaryResponse;
import com.netbanking.account.domain.BankAccount;
import com.netbanking.account.repository.AccountHolderRepository;
import com.netbanking.account.repository.BankAccountRepository;
import com.netbanking.common.exception.ResourceNotFoundException;
import com.netbanking.discovery.CustomerDirectory;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class AccountService {

    private final BankAccountRepository bankAccountRepository;
    private final AccountHolderRepository accountHolderRepository;
    private final CustomerDirectory customerService;

    public AccountService(
            BankAccountRepository bankAccountRepository,
            AccountHolderRepository accountHolderRepository,
            CustomerDirectory customerService) {
        this.bankAccountRepository = bankAccountRepository;
        this.accountHolderRepository = accountHolderRepository;
        this.customerService = customerService;
    }

    public List<AccountSummaryResponse> getAccountsForUser(Long userId) {
        Long customerId = customerService.requireCustomerIdForUser(userId);
        return bankAccountRepository
                .findAllById(accountHolderRepository.findActiveAccountIdsByCustomerId(customerId))
                .stream()
                .sorted(Comparator.comparing(BankAccount::getAccountNumber))
                .map(this::toResponse)
                .toList();
    }

    public AccountSummaryResponse getOwnedAccount(Long userId, Long accountId) {
        requireOwnership(userId, accountId);
        return toResponse(findAccount(accountId));
    }

    public void requireOwnership(Long userId, Long accountId) {
        Long customerId = customerService.requireCustomerIdForUser(userId);
        if (!accountHolderRepository.existsByAccountIdAndCustomerIdAndIsActive(
                accountId, customerId, "Y")) {
            throw new AccessDeniedException("You do not have access to this account.");
        }
    }

    @Transactional
    public AccountSummaryResponse changeAccountStatus(Long accountId, AccountStatus accountStatus) {
        BankAccount account = findAccount(accountId);
        account.changeStatus(accountStatus.name());
        return toResponse(account);
    }

    private BankAccount findAccount(Long accountId) {
        return bankAccountRepository
                .findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account was not found."));
    }

    private AccountSummaryResponse toResponse(BankAccount account) {
        return new AccountSummaryResponse(
                account.getAccountId(),
                account.getBranchId(),
                account.getAccountNumber(),
                account.getAccountType(),
                account.getCurrencyCode(),
                account.getAccountStatus(),
                account.getCurrentBalance(),
                account.getAvailableBalance());
    }
}
