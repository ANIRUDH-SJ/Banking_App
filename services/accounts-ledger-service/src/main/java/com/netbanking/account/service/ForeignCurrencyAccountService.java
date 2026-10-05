package com.netbanking.account.service;

import com.netbanking.account.api.AccountSummaryResponse;
import com.netbanking.account.domain.AccountHolder;
import com.netbanking.account.domain.BankAccount;
import com.netbanking.account.repository.AccountHolderRepository;
import com.netbanking.account.repository.BankAccountRepository;
import com.netbanking.branch.repository.BranchRepository;
import com.netbanking.common.exception.ResourceNotFoundException;
import com.netbanking.discovery.CustomerDirectory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Currency;
import java.util.Locale;
import java.util.Set;

@Service
public class ForeignCurrencyAccountService {
    private static final Set<String> SUPPORTED = Set.of("USD", "EUR", "GBP");
    private final CustomerDirectory customers;
    private final BankAccountRepository accounts;
    private final AccountHolderRepository holders;
    private final BranchRepository branches;
    private final AccountService views;
    private final String onboardingIfsc;

    public ForeignCurrencyAccountService(CustomerDirectory customers, BankAccountRepository accounts,
            AccountHolderRepository holders, BranchRepository branches, AccountService views,
            @Value("${app.onboarding.branch-ifsc:NETB0000001}") String onboardingIfsc) {
        this.customers = customers;
        this.accounts = accounts;
        this.holders = holders;
        this.branches = branches;
        this.views = views;
        this.onboardingIfsc = onboardingIfsc;
    }

    @Transactional
    public AccountSummaryResponse open(Long userId, String currencyCode) {
        if (!SUPPORTED.contains(currencyCode))
            throw new IllegalArgumentException("Supported foreign currencies are USD, EUR, and GBP.");
        Long customerId = customers.requireCustomerIdForUser(userId);
        if (customerId <= 0 || customerId > 999_999_999_999_999L)
            throw new IllegalArgumentException("Customer identifier is outside the account-number range.");
        String accountNumber = "20" + String.format(Locale.ROOT, "%015d%03d", customerId,
                Currency.getInstance(currencyCode).getNumericCode());
        var existing = accounts.findAccountIdByAccountNumber(accountNumber);
        if (existing.isPresent()) return views.getOwnedAccount(userId, existing.get());
        var branch = branches.findByIfscCodeAndIsActive(onboardingIfsc, "Y")
                .orElseThrow(() -> new ResourceNotFoundException("The onboarding branch is not available."));
        BankAccount account = accounts.saveAndFlush(
                BankAccount.openSavings(branch.getBranchId(), accountNumber, currencyCode));
        holders.saveAndFlush(new AccountHolder(account.getAccountId(), customerId));
        return views.getOwnedAccount(userId, account.getAccountId());
    }
}
