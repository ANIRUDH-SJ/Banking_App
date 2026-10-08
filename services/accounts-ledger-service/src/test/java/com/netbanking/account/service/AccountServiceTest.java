package com.netbanking.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.netbanking.account.repository.AccountHolderRepository;
import com.netbanking.account.repository.BankAccountRepository;
import com.netbanking.account.domain.AccountHolder;
import com.netbanking.account.domain.BankAccount;
import com.netbanking.bank.domain.Bank;
import com.netbanking.bank.repository.BankRepository;
import com.netbanking.branch.domain.Branch;
import com.netbanking.branch.repository.BranchRepository;
import com.netbanking.discovery.CustomerDirectory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock private BankAccountRepository bankAccountRepository;

    @Mock private AccountHolderRepository accountHolderRepository;

    @Mock private CustomerDirectory customerService;

    @Mock private BranchRepository branchRepository;

    @Mock private BankRepository bankRepository;

    @Test
    void allowsAnOwnedAccount() {
        when(customerService.requireCustomerIdForUser(7L)).thenReturn(15L);
        when(accountHolderRepository.existsByAccountIdAndCustomerIdAndIsActive(40L, 15L, "Y"))
                .thenReturn(true);

        AccountService service =
                new AccountService(bankAccountRepository, accountHolderRepository, customerService,
                        branchRepository, bankRepository);

        assertThatCode(() -> service.requireOwnership(7L, 40L)).doesNotThrowAnyException();
    }

    @Test
    void deniesAnAccountOwnedByAnotherCustomer() {
        when(customerService.requireCustomerIdForUser(7L)).thenReturn(15L);
        when(accountHolderRepository.existsByAccountIdAndCustomerIdAndIsActive(40L, 15L, "Y"))
                .thenReturn(false);

        AccountService service =
                new AccountService(bankAccountRepository, accountHolderRepository, customerService,
                        branchRepository, bankRepository);

        assertThatThrownBy(() -> service.requireOwnership(7L, 40L))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void includesVerifiedBankAndBranchDetailsForAnOwnedAccount() {
        AccountHolder holder = mock(AccountHolder.class);
        BankAccount account = mock(BankAccount.class);
        Branch branch = mock(Branch.class);
        Bank bank = mock(Bank.class);
        when(customerService.requireCustomerIdForUser(7L)).thenReturn(15L);
        when(accountHolderRepository.findByAccountIdAndCustomerIdAndIsActive(40L, 15L, "Y"))
                .thenReturn(Optional.of(holder));
        when(bankAccountRepository.findById(40L)).thenReturn(Optional.of(account));
        when(account.getBranchId()).thenReturn(9L);
        when(account.getAccountNumber()).thenReturn("001234567890");
        when(account.getAvailableBalance()).thenReturn(new BigDecimal("500.00"));
        when(branchRepository.findById(9L)).thenReturn(Optional.of(branch));
        when(branch.getBankId()).thenReturn(3L);
        when(branch.getIfscCode()).thenReturn("NETB0000001");
        when(branch.getBranchName()).thenReturn("Main branch");
        when(branch.getCity()).thenReturn("Bengaluru");
        when(branch.getState()).thenReturn("Karnataka");
        when(bankRepository.findById(3L)).thenReturn(Optional.of(bank));
        when(bank.getDisplayName()).thenReturn("ORACLE INTERNATIONAL BANK (OIB)");

        var response = new AccountService(bankAccountRepository, accountHolderRepository,
                customerService, branchRepository, bankRepository).getOwnedAccount(7L, 40L);

        assertThat(response.accountNumber()).isEqualTo("001234567890");
        assertThat(response.ifscCode()).isEqualTo("NETB0000001");
        assertThat(response.bankName()).isEqualTo("ORACLE INTERNATIONAL BANK (OIB)");
        assertThat(response.branchName()).isEqualTo("Main branch");
        assertThat(response.branchCity()).isEqualTo("Bengaluru");
        assertThat(response.availableBalance()).isEqualByComparingTo("500.00");
    }
}
