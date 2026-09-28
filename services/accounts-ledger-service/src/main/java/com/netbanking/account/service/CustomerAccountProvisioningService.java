package com.netbanking.account.service;

import com.netbanking.account.domain.AccountHolder;
import com.netbanking.account.domain.BankAccount;
import com.netbanking.account.repository.AccountHolderRepository;
import com.netbanking.account.repository.BankAccountRepository;
import com.netbanking.branch.domain.Branch;
import com.netbanking.branch.repository.BranchRepository;
import com.netbanking.common.exception.ResourceNotFoundException;
import com.netbanking.contracts.CustomerRegistered;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class CustomerAccountProvisioningService {
    private final BankAccountRepository accounts;
    private final AccountHolderRepository holders;
    private final BranchRepository branches;
    private final String onboardingIfsc;

    public CustomerAccountProvisioningService(
            BankAccountRepository accounts,
            AccountHolderRepository holders,
            BranchRepository branches,
            @Value("${app.onboarding.branch-ifsc:NETB0000001}") String onboardingIfsc) {
        this.accounts = accounts;
        this.holders = holders;
        this.branches = branches;
        this.onboardingIfsc = onboardingIfsc;
    }

    @Transactional
    public void provision(CustomerRegistered customer) {
        if (customer.customerId() == null || customer.customerId() <= 0) {
            throw new IllegalArgumentException("Customer identifier must be positive.");
        }
        if (holders.existsByCustomerIdAndIsActive(customer.customerId(), "Y")) {
            return;
        }
        Branch branch =
                branches
                        .findByIfscCodeAndIsActive(onboardingIfsc, "Y")
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "The onboarding branch is not available."));
        BankAccount account =
                accounts.saveAndFlush(
                        BankAccount.openSavings(
                                branch.getBranchId(), accountNumber(customer.customerId())));
        holders.saveAndFlush(new AccountHolder(account.getAccountId(), customer.customerId()));
    }

    private static String accountNumber(Long customerId) {
        return "10" + String.format(Locale.ROOT, "%016d", customerId);
    }
}
