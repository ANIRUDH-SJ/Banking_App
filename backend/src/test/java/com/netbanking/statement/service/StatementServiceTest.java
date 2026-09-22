package com.netbanking.statement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.netbanking.account.service.AccountService;
import com.netbanking.statement.api.StatementFilter;
import com.netbanking.transaction.domain.AccountTransactionEntry;
import com.netbanking.transaction.domain.BankTransaction;
import com.netbanking.transaction.repository.AccountTransactionEntryRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class StatementServiceTest {

    @Mock private AccountService accountService;
    @Mock private AccountTransactionEntryRepository entryRepository;

    @Test
    void rejectsAnUnownedAccountBeforeQuerying() {
        org.mockito.Mockito.doThrow(new AccessDeniedException("denied"))
                .when(accountService).requireOwnership(7L, 10L);
        StatementService service = new StatementService(accountService, entryRepository);

        assertThatThrownBy(() -> service.getStatement(7L, 10L, emptyFilter(), 0, 20))
                .isInstanceOf(AccessDeniedException.class);
        verify(entryRepository, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void rejectsInvertedDates() {
        assertThatThrownBy(() -> new StatementFilter(LocalDate.of(2026, 8, 2),
                LocalDate.of(2026, 8, 1), null, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void exportsEscapedCsvFromTheOwnedAccount() {
        AccountTransactionEntry entry = entry(" =SUM(1,2)\n\"test\"");
        when(entryRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entry)));
        StatementService service = new StatementService(accountService, entryRepository);

        String csv = new String(service.exportCsv(7L, 10L, emptyFilter()), StandardCharsets.UTF_8);

        verify(accountService).requireOwnership(7L, 10L);
        assertThat(csv).startsWith("entry_id,transaction_id,reference");
        assertThat(csv).contains("\"' =SUM(1,2)\n\"\"test\"\"\"");
        assertThat(csv).contains("12.50,\"INR\",87.50");
    }

    @Test
    void rejectsAnOversizedExport() {
        when(entryRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entry("ok")), PageRequest.of(0, 500), 10_001));
        StatementService service = new StatementService(accountService, entryRepository);

        assertThatThrownBy(() -> service.exportCsv(7L, 10L, emptyFilter()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("413");
    }

    private static StatementFilter emptyFilter() {
        return new StatementFilter(null, null, null, null);
    }

    private static AccountTransactionEntry entry(String narration) {
        BankTransaction transaction = BeanUtils.instantiateClass(BankTransaction.class);
        ReflectionTestUtils.setField(transaction, "transactionId", 1L);
        ReflectionTestUtils.setField(transaction, "transactionReference", "TXN-123");
        ReflectionTestUtils.setField(transaction, "transactionType", "TRANSFER");
        ReflectionTestUtils.setField(transaction, "transactionStatus", "COMPLETED");
        ReflectionTestUtils.setField(transaction, "currencyCode", "INR");
        ReflectionTestUtils.setField(transaction, "narration", narration);
        AccountTransactionEntry entry = BeanUtils.instantiateClass(AccountTransactionEntry.class);
        ReflectionTestUtils.setField(entry, "entryId", 99L);
        ReflectionTestUtils.setField(entry, "accountId", 10L);
        ReflectionTestUtils.setField(entry, "transaction", transaction);
        ReflectionTestUtils.setField(entry, "entryType", "DEBIT");
        ReflectionTestUtils.setField(entry, "amount", new BigDecimal("12.50"));
        ReflectionTestUtils.setField(entry, "balanceAfter", new BigDecimal("87.50"));
        ReflectionTestUtils.setField(entry, "postedAt", LocalDateTime.of(2026, 8, 1, 12, 30));
        return entry;
    }
}
