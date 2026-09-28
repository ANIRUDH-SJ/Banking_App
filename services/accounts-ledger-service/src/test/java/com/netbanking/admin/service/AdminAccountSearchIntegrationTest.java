package com.netbanking.admin.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.netbanking.ServiceTestBase;
import com.netbanking.account.api.AccountStatus;
import com.netbanking.account.domain.AccountHolder;
import com.netbanking.account.domain.BankAccount;
import com.netbanking.account.repository.AccountHolderRepository;
import com.netbanking.account.repository.BankAccountRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

class AdminAccountSearchIntegrationTest extends ServiceTestBase {
    @Autowired private AdminAccountService service;
    @Autowired private BankAccountRepository accounts;
    @Autowired private AccountHolderRepository holders;

    @Test
    @Transactional
    void filtersAccountsByNumberOwnerAndStatus() {
        BankAccount account = accounts.saveAndFlush(BankAccount.openSavings(2L, "908070605040"));
        holders.saveAndFlush(new AccountHolder(account.getAccountId(), 909001L));

        var result =
                service.search("70605", 909001L, AccountStatus.ACTIVE, 0, 20);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).accountNumber()).isEqualTo("908070605040");
        assertThat(result.getContent().get(0).customerIds()).containsExactly(909001L);
    }
}
