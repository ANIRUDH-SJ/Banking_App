package com.netbanking.account.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.netbanking.account.api.AccountStatus;
import com.netbanking.account.domain.AccountHolder;
import com.netbanking.account.domain.BankAccount;
import com.netbanking.account.repository.AccountHolderRepository;
import com.netbanking.account.repository.BankAccountRepository;
import com.netbanking.audit.service.AuditLogService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class AdminAccountServiceTest {
    @Mock private BankAccountRepository accounts;
    @Mock private AccountHolderRepository holders;
    @Mock private AuditLogService audit;

    @Test
    void searchReturnsAccountOwnershipContextInOneBatch() {
        BankAccount account = account();
        when(accounts.findAll(
                        org.mockito.ArgumentMatchers.<Specification<BankAccount>>any(),
                        any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(account)));
        when(holders.findByAccountIdInAndIsActive(List.of(3L), "Y"))
                .thenReturn(List.of(new AccountHolder(3L, 11L)));

        var result = service().search("1234", 11L, AccountStatus.ACTIVE, 0, 20);

        assertThat(result.getContent().get(0).customerIds()).containsExactly(11L);
        verify(holders).findByAccountIdInAndIsActive(List.of(3L), "Y");
    }

    @Test
    void administratorCanFreezeAnAccountAndTheActionIsAudited() {
        BankAccount account = account();
        when(accounts.findByIdForUpdate(3L)).thenReturn(Optional.of(account));
        when(holders.findByAccountIdInAndIsActive(List.of(3L), "Y"))
                .thenReturn(List.of(new AccountHolder(3L, 11L)));

        var result = service().changeStatus(99L, 3L, AccountStatus.FROZEN);

        assertThat(result.status()).isEqualTo("FROZEN");
        verify(audit)
                .record(
                        99L,
                        "ADMIN_ACCOUNT_STATUS_CHANGED",
                        "ACCOUNT",
                        "3",
                        "SUCCESS",
                        "from=ACTIVE;to=FROZEN");
    }

    private AdminAccountService service() {
        return new AdminAccountService(accounts, holders, audit);
    }

    private static BankAccount account() {
        BankAccount account = BankAccount.openSavings(2L, "123456789012");
        ReflectionTestUtils.setField(account, "accountId", 3L);
        return account;
    }
}
