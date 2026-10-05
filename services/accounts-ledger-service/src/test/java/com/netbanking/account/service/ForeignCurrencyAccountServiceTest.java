package com.netbanking.account.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.netbanking.account.api.AccountSummaryResponse;
import com.netbanking.account.domain.BankAccount;
import com.netbanking.account.repository.AccountHolderRepository;
import com.netbanking.account.repository.BankAccountRepository;
import com.netbanking.branch.domain.Branch;
import com.netbanking.branch.repository.BranchRepository;
import com.netbanking.discovery.CustomerDirectory;
import org.junit.jupiter.api.Test;

import java.util.Optional;

class ForeignCurrencyAccountServiceTest {
    final CustomerDirectory customers = mock(CustomerDirectory.class);
    final BankAccountRepository accounts = mock(BankAccountRepository.class);
    final AccountHolderRepository holders = mock(AccountHolderRepository.class);
    final BranchRepository branches = mock(BranchRepository.class);
    final AccountService views = mock(AccountService.class);
    final ForeignCurrencyAccountService service = new ForeignCurrencyAccountService(
            customers, accounts, holders, branches, views, "NETB0000001");

    @Test
    void existingWalletIsReturnedWithoutOpeningAnother() {
        when(customers.requireCustomerIdForUser(7L)).thenReturn(11L);
        when(accounts.findAccountIdByAccountNumber("20000000000000011840"))
                .thenReturn(Optional.of(91L));
        when(views.getOwnedAccount(7L, 91L)).thenReturn(mock(AccountSummaryResponse.class));

        service.open(7L, "USD");

        verify(views).getOwnedAccount(7L, 91L);
        verify(accounts, never()).saveAndFlush(any(BankAccount.class));
    }

    @Test
    void unsupportedCurrencyCannotOpenAnAccount() {
        assertThatThrownBy(() -> service.open(7L, "XYZ"))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(customers, accounts, holders, branches);
    }
}
