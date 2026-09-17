package com.netbanking.account.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.netbanking.account.repository.AccountHolderRepository;
import com.netbanking.account.repository.BankAccountRepository;
import com.netbanking.customer.service.CustomerService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private BankAccountRepository bankAccountRepository;

    @Mock
    private AccountHolderRepository accountHolderRepository;

    @Mock
    private CustomerService customerService;

    @Test
    void allowsAnOwnedAccount() {
        when(customerService.requireCustomerIdForUser(7L)).thenReturn(15L);
        when(accountHolderRepository.existsByAccountIdAndCustomerIdAndIsActive(40L, 15L, "Y"))
                .thenReturn(true);

        AccountService service = new AccountService(bankAccountRepository, accountHolderRepository, customerService);

        assertThatCode(() -> service.requireOwnership(7L, 40L)).doesNotThrowAnyException();
    }

    @Test
    void deniesAnAccountOwnedByAnotherCustomer() {
        when(customerService.requireCustomerIdForUser(7L)).thenReturn(15L);
        when(accountHolderRepository.existsByAccountIdAndCustomerIdAndIsActive(40L, 15L, "Y"))
                .thenReturn(false);

        AccountService service = new AccountService(bankAccountRepository, accountHolderRepository, customerService);

        assertThatThrownBy(() -> service.requireOwnership(7L, 40L))
                .isInstanceOf(AccessDeniedException.class);
    }
}
