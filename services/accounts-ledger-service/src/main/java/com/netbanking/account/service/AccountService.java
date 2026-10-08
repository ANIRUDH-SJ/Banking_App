package com.netbanking.account.service;

import com.netbanking.account.api.AccountStatus;
import com.netbanking.account.api.AccountSummaryResponse;
import com.netbanking.account.domain.AccountHolder;
import com.netbanking.account.domain.BankAccount;
import com.netbanking.account.repository.AccountHolderRepository;
import com.netbanking.account.repository.BankAccountRepository;
import com.netbanking.bank.repository.BankRepository;
import com.netbanking.branch.repository.BranchRepository;
import com.netbanking.common.exception.ResourceNotFoundException;
import com.netbanking.discovery.CustomerDirectory;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class AccountService {

    private final BankAccountRepository bankAccountRepository;
    private final AccountHolderRepository accountHolderRepository;
    private final CustomerDirectory customerService;
    private final BranchRepository branchRepository;
    private final BankRepository bankRepository;

    public AccountService(
            BankAccountRepository bankAccountRepository,
            AccountHolderRepository accountHolderRepository,
            CustomerDirectory customerService,
            BranchRepository branchRepository,
            BankRepository bankRepository) {
        this.bankAccountRepository = bankAccountRepository;
        this.accountHolderRepository = accountHolderRepository;
        this.customerService = customerService;
        this.branchRepository = branchRepository;
        this.bankRepository = bankRepository;
    }

    public List<AccountSummaryResponse> getAccountsForUser(Long userId) {
        Long customerId = customerService.requireCustomerIdForUser(userId);
        Map<Long, String> nicknames = new HashMap<>();
        accountHolderRepository
                .findByCustomerIdAndIsActive(customerId, "Y")
                .forEach(holder -> nicknames.put(holder.getAccountId(), holder.getNickname()));
        return bankAccountRepository.findAllById(nicknames.keySet()).stream()
                .sorted(Comparator.comparing(BankAccount::getAccountNumber))
                .map(account -> toResponse(account, nicknames.get(account.getAccountId())))
                .toList();
    }

    public AccountSummaryResponse getOwnedAccount(Long userId, Long accountId) {
        AccountHolder holder = requireHolder(userId, accountId);
        return toResponse(findAccount(accountId), holder.getNickname());
    }

    @Transactional
    public AccountSummaryResponse rename(Long userId, Long accountId, String nickname) {
        AccountHolder holder = requireHolder(userId, accountId);
        holder.rename(nickname);
        return toResponse(findAccount(accountId), holder.getNickname());
    }

    private AccountHolder requireHolder(Long userId, Long accountId) {
        Long customerId = customerService.requireCustomerIdForUser(userId);
        return accountHolderRepository
                .findByAccountIdAndCustomerIdAndIsActive(accountId, customerId, "Y")
                .orElseThrow(() -> new AccessDeniedException("You do not have access to this account."));
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
        return toResponse(account, null);
    }

    private BankAccount findAccount(Long accountId) {
        return bankAccountRepository
                .findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account was not found."));
    }

    private AccountSummaryResponse toResponse(BankAccount account, String nickname) {
        var branch = branchRepository.findById(account.getBranchId())
                .orElseThrow(() -> new ResourceNotFoundException("Account branch was not found."));
        var bank = bankRepository.findById(branch.getBankId())
                .orElseThrow(() -> new ResourceNotFoundException("Account bank was not found."));
        return new AccountSummaryResponse(
                account.getAccountId(),
                account.getBranchId(),
                account.getAccountNumber(),
                account.getAccountType(),
                account.getCurrencyCode(),
                account.getAccountStatus(),
                account.getCurrentBalance(),
                account.getAvailableBalance(),
                nickname,
                bank.getDisplayName(),
                branch.getBranchName(),
                branch.getCity(),
                branch.getState(),
                branch.getIfscCode());
    }
}
