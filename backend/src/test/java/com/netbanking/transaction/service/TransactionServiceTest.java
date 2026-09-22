package com.netbanking.transaction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.netbanking.account.service.AccountService;
import com.netbanking.common.exception.ResourceNotFoundException;
import com.netbanking.transaction.domain.AccountTransactionEntry;
import com.netbanking.transaction.domain.BankTransaction;
import com.netbanking.transaction.repository.AccountTransactionEntryRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock private AccountService accountService;
    @Mock private AccountTransactionEntryRepository entryRepository;

    @Test
    void listsOnlyEntriesForTheOwnedAccount() {
        AccountTransactionEntry entry = entry(10L);
        when(entryRepository.findByAccountId(any(), any())).thenReturn(new PageImpl<>(List.of(entry)));
        TransactionService service = new TransactionService(accountService, entryRepository);

        var page = service.getAccountTransactions(7L, 10L, 0, 20);

        verify(accountService).requireOwnership(7L, 10L);
        verify(entryRepository).findByAccountId(org.mockito.ArgumentMatchers.eq(10L), any(Pageable.class));
        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).reference()).isEqualTo("TXN-123");
        assertThat(page.getContent().get(0).entryType()).isEqualTo("DEBIT");
        assertThat(page.getContent().get(0).currencyCode()).isEqualTo("INR");
    }

    @Test
    void rejectsAnUnownedAccountBeforeQueryingHistory() {
        org.mockito.Mockito.doThrow(new AccessDeniedException("denied"))
                .when(accountService).requireOwnership(7L, 10L);
        TransactionService service = new TransactionService(accountService, entryRepository);

        assertThatThrownBy(() -> service.getAccountTransactions(7L, 10L, 0, 20))
                .isInstanceOf(AccessDeniedException.class);
        verify(entryRepository, never()).findByAccountId(any(), any());
    }

    @Test
    void scopesSingleEntryLookupToTheAccount() {
        when(entryRepository.findByEntryIdAndAccountId(99L, 10L)).thenReturn(Optional.empty());
        TransactionService service = new TransactionService(accountService, entryRepository);

        assertThatThrownBy(() -> service.getAccountTransaction(7L, 10L, 99L))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(accountService).requireOwnership(7L, 10L);
    }

    @Test
    void rejectsUnboundedPageSize() {
        TransactionService service = new TransactionService(accountService, entryRepository);

        assertThatThrownBy(() -> service.getAccountTransactions(7L, 10L, 0, 101))
                .isInstanceOf(IllegalArgumentException.class);
        verify(entryRepository, never()).findByAccountId(any(), any());
    }

    private static AccountTransactionEntry entry(Long accountId) {
        BankTransaction transaction = BeanUtils.instantiateClass(BankTransaction.class);
        ReflectionTestUtils.setField(transaction, "transactionId", 1L);
        ReflectionTestUtils.setField(transaction, "transactionReference", "TXN-123");
        ReflectionTestUtils.setField(transaction, "transactionType", "TRANSFER");
        ReflectionTestUtils.setField(transaction, "transactionStatus", "COMPLETED");
        ReflectionTestUtils.setField(transaction, "currencyCode", "INR");
        AccountTransactionEntry entry = BeanUtils.instantiateClass(AccountTransactionEntry.class);
        ReflectionTestUtils.setField(entry, "entryId", 99L);
        ReflectionTestUtils.setField(entry, "accountId", accountId);
        ReflectionTestUtils.setField(entry, "transaction", transaction);
        ReflectionTestUtils.setField(entry, "entryType", "DEBIT");
        ReflectionTestUtils.setField(entry, "amount", new BigDecimal("12.50"));
        ReflectionTestUtils.setField(entry, "balanceAfter", new BigDecimal("87.50"));
        ReflectionTestUtils.setField(entry, "postedAt", LocalDateTime.of(2026, 8, 1, 12, 30));
        return entry;
    }
}
