package com.netbanking.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.netbanking.account.domain.AccountHolder;
import com.netbanking.account.domain.BankAccount;
import com.netbanking.account.repository.AccountHolderRepository;
import com.netbanking.account.repository.BankAccountRepository;
import com.netbanking.branch.domain.Branch;
import com.netbanking.branch.repository.BranchRepository;
import com.netbanking.contracts.CustomerRegistered;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class CustomerAccountProvisioningServiceTest {
    @Mock private BankAccountRepository accounts;
    @Mock private AccountHolderRepository holders;
    @Mock private BranchRepository branches;
    @Mock private Branch branch;

    @Test
    void opensAnActiveSavingsAccountForANewCustomer() {
        when(branch.getBranchId()).thenReturn(12L);
        when(branches.findByIfscCodeAndIsActive("NETB0000001", "Y"))
                .thenReturn(Optional.of(branch));
        when(accounts.saveAndFlush(any(BankAccount.class)))
                .thenAnswer(
                        invocation -> {
                            BankAccount saved = invocation.getArgument(0);
                            ReflectionTestUtils.setField(saved, "accountId", 44L);
                            return saved;
                        });

        service().provision(new CustomerRegistered(91L, "CUST0000000000000091"));

        ArgumentCaptor<BankAccount> account = ArgumentCaptor.forClass(BankAccount.class);
        verify(accounts).saveAndFlush(account.capture());
        assertThat(account.getValue().getBranchId()).isEqualTo(12L);
        assertThat(account.getValue().getAccountNumber()).isEqualTo("100000000000000091");
        assertThat(account.getValue().getAccountType()).isEqualTo("SAVINGS");
        assertThat(account.getValue().getAccountStatus()).isEqualTo("ACTIVE");
        assertThat(account.getValue().getCurrentBalance()).isEqualByComparingTo("30000.00");
        assertThat(account.getValue().getAvailableBalance()).isEqualByComparingTo("30000.00");

        ArgumentCaptor<AccountHolder> holder = ArgumentCaptor.forClass(AccountHolder.class);
        verify(holders).saveAndFlush(holder.capture());
        assertThat(holder.getValue().getAccountId()).isEqualTo(44L);
        assertThat(holder.getValue().getCustomerId()).isEqualTo(91L);
        assertThat(holder.getValue().getHolderType()).isEqualTo("PRIMARY");
    }

    @Test
    void doesNotOpenASecondAccountWhenTheEventIsReplayed() {
        when(holders.existsByCustomerIdAndIsActive(91L, "Y")).thenReturn(true);

        service().provision(new CustomerRegistered(91L, "CUST0000000000000091"));

        verify(accounts, never()).saveAndFlush(any());
        verify(branches, never()).findByIfscCodeAndIsActive(any(), any());
    }

    private CustomerAccountProvisioningService service() {
        return new CustomerAccountProvisioningService(
                accounts, holders, branches, "NETB0000001", new BigDecimal("30000"));
    }
}
