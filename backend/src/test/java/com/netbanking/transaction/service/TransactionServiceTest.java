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
import com.netbanking.transaction.domain.EntryType;
import com.netbanking.transaction.domain.TransactionStatus;
import com.netbanking.transaction.domain.TransactionStatusHistory;
import com.netbanking.transaction.domain.TransactionType;
import com.netbanking.transaction.repository.AccountTransactionEntryRepository;
import com.netbanking.transaction.repository.BankTransactionRepository;
import com.netbanking.transaction.repository.TransactionStatusHistoryRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock private AccountService accountService;
    @Mock private BankTransactionRepository transactionRepository;
    @Mock private AccountTransactionEntryRepository entryRepository;
    @Mock private TransactionStatusHistoryRepository statusHistoryRepository;
    @Mock private TransactionReferenceGenerator referenceGenerator;

    @Test
    void listsOnlyEntriesForTheOwnedAccount() {
        AccountTransactionEntry entry = entry(10L);
        when(entryRepository.findByAccountId(any(), any())).thenReturn(new PageImpl<>(List.of(entry)));

        var page = service().getAccountTransactions(7L, 10L, 0, 20);

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

        assertThatThrownBy(() -> service().getAccountTransactions(7L, 10L, 0, 20))
                .isInstanceOf(AccessDeniedException.class);
        verify(entryRepository, never()).findByAccountId(any(), any());
    }

    @Test
    void scopesSingleEntryLookupToTheAccount() {
        when(entryRepository.findByEntryIdAndAccountId(99L, 10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().getAccountTransaction(7L, 10L, 99L))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(accountService).requireOwnership(7L, 10L);
    }

    @Test
    void rejectsUnboundedPageSize() {
        assertThatThrownBy(() -> service().getAccountTransactions(7L, 10L, 0, 101))
                .isInstanceOf(IllegalArgumentException.class);
        verify(entryRepository, never()).findByAccountId(any(), any());
    }

    @Test
    void createsPendingTransactionWithStableReferenceAndHistory() {
        when(referenceGenerator.generate()).thenReturn("TXN-UNIQUE");
        when(transactionRepository.existsByTransactionReference("TXN-UNIQUE")).thenReturn(false);
        when(transactionRepository.save(any(BankTransaction.class))).thenAnswer(invocation -> {
            BankTransaction transaction = invocation.getArgument(0);
            ReflectionTestUtils.setField(transaction, "transactionId", 44L);
            return transaction;
        });
        CreateTransactionCommand command = new CreateTransactionCommand(
                10L, 20L, 30L, 7L, TransactionType.TRANSFER,
                new BigDecimal("12.50"), "inr", " Rent ");

        TransactionRecord created = service().createTransaction(command);

        assertThat(created.transactionId()).isEqualTo(44L);
        assertThat(created.reference()).isEqualTo("TXN-UNIQUE");
        assertThat(created.status()).isEqualTo(TransactionStatus.PENDING);
        assertThat(created.currencyCode()).isEqualTo("INR");
        ArgumentCaptor<TransactionStatusHistory> history = ArgumentCaptor.forClass(TransactionStatusHistory.class);
        verify(statusHistoryRepository).save(history.capture());
        assertThat(history.getValue().getPreviousStatus()).isNull();
        assertThat(history.getValue().getNewStatus()).isEqualTo(TransactionStatus.PENDING);
    }

    @Test
    void rejectsInvalidAccountShapeBeforeCreatingTransaction() {
        CreateTransactionCommand command = new CreateTransactionCommand(
                10L, 10L, null, 7L, TransactionType.TRANSFER,
                BigDecimal.ONE, "INR", null);

        assertThatThrownBy(() -> service().createTransaction(command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("different");
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void recordsEveryAcceptedStatusTransition() {
        BankTransaction transaction = transaction(TransactionStatus.PENDING);
        when(transactionRepository.findById(1L)).thenReturn(Optional.of(transaction));

        TransactionRecord processing = service().changeStatus(
                1L, TransactionStatus.PROCESSING, 7L, null);

        assertThat(processing.status()).isEqualTo(TransactionStatus.PROCESSING);
        ArgumentCaptor<TransactionStatusHistory> history = ArgumentCaptor.forClass(TransactionStatusHistory.class);
        verify(statusHistoryRepository).save(history.capture());
        assertThat(history.getValue().getPreviousStatus()).isEqualTo(TransactionStatus.PENDING);
        assertThat(history.getValue().getNewStatus()).isEqualTo(TransactionStatus.PROCESSING);
    }

    @Test
    void refusesCompletionUntilRequiredLedgerEntriesExist() {
        BankTransaction transaction = transaction(TransactionStatus.PROCESSING);
        when(transactionRepository.findById(1L)).thenReturn(Optional.of(transaction));

        assertThatThrownBy(() -> service().changeStatus(
                1L, TransactionStatus.COMPLETED, 7L, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("debit ledger entry");
        verify(statusHistoryRepository, never()).save(any());
    }

    @Test
    void completesAfterBothRequiredLedgerEntriesExist() {
        BankTransaction transaction = transaction(TransactionStatus.PROCESSING);
        when(transactionRepository.findById(1L)).thenReturn(Optional.of(transaction));
        when(entryRepository.existsByTransactionTransactionIdAndAccountIdAndEntryType(
                1L, 10L, EntryType.DEBIT)).thenReturn(true);
        when(entryRepository.existsByTransactionTransactionIdAndAccountIdAndEntryType(
                1L, 20L, EntryType.CREDIT)).thenReturn(true);

        TransactionRecord completed = service().changeStatus(
                1L, TransactionStatus.COMPLETED, 7L, null);

        assertThat(completed.status()).isEqualTo(TransactionStatus.COMPLETED);
        verify(statusHistoryRepository).save(any(TransactionStatusHistory.class));
    }

    @Test
    void postsMatchingEntryOnlyWhileProcessing() {
        BankTransaction transaction = transaction(TransactionStatus.PROCESSING);
        when(transactionRepository.findById(1L)).thenReturn(Optional.of(transaction));
        when(entryRepository.save(any(AccountTransactionEntry.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var posted = service().postEntry(1L, 10L, EntryType.DEBIT, new BigDecimal("87.50"));

        assertThat(posted.entryType()).isEqualTo("DEBIT");
        assertThat(posted.balanceAfter()).isEqualByComparingTo("87.50");
    }

    @Test
    void rejectsEntryForAnUnrelatedAccount() {
        BankTransaction transaction = transaction(TransactionStatus.PROCESSING);
        when(transactionRepository.findById(1L)).thenReturn(Optional.of(transaction));

        assertThatThrownBy(() -> service().postEntry(
                1L, 99L, EntryType.DEBIT, new BigDecimal("87.50")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not match");
        verify(entryRepository, never()).save(any());
    }

    private TransactionService service() {
        return new TransactionService(accountService, transactionRepository, entryRepository,
                statusHistoryRepository, referenceGenerator);
    }

    private static BankTransaction transaction(TransactionStatus status) {
        BankTransaction transaction = new BankTransaction(
                "TXN-123", 10L, 20L, 30L, 7L, TransactionType.TRANSFER,
                new BigDecimal("12.50"), "INR", "Rent");
        ReflectionTestUtils.setField(transaction, "transactionId", 1L);
        ReflectionTestUtils.setField(transaction, "transactionStatus", status);
        return transaction;
    }

    private static AccountTransactionEntry entry(Long accountId) {
        BankTransaction transaction = transaction(TransactionStatus.COMPLETED);
        AccountTransactionEntry entry = BeanUtils.instantiateClass(AccountTransactionEntry.class);
        ReflectionTestUtils.setField(entry, "entryId", 99L);
        ReflectionTestUtils.setField(entry, "accountId", accountId);
        ReflectionTestUtils.setField(entry, "transaction", transaction);
        ReflectionTestUtils.setField(entry, "entryType", EntryType.DEBIT);
        ReflectionTestUtils.setField(entry, "amount", new BigDecimal("12.50"));
        ReflectionTestUtils.setField(entry, "balanceAfter", new BigDecimal("87.50"));
        ReflectionTestUtils.setField(entry, "postedAt", LocalDateTime.of(2026, 8, 1, 12, 30));
        return entry;
    }
}
