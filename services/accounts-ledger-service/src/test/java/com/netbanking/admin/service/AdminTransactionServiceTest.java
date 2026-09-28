package com.netbanking.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.netbanking.ServiceTestBase;
import com.netbanking.admin.api.AdminTransactionSearchFilter;
import com.netbanking.transaction.domain.BankTransaction;
import com.netbanking.transaction.domain.TransactionStatus;
import com.netbanking.transaction.domain.TransactionType;
import com.netbanking.transaction.repository.BankTransactionRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

class AdminTransactionServiceTest extends ServiceTestBase {
    @Autowired private AdminTransactionService service;
    @Autowired private BankTransactionRepository repository;

    @Test
    @Transactional
    void filtersTransactionMonitorByReferenceAccountUserTypeStatusAndDate() {
        repository.saveAndFlush(
                new BankTransaction(
                        "ADM-MONITOR-0001",
                        101L,
                        202L,
                        303L,
                        7L,
                        TransactionType.TRANSFER,
                        new BigDecimal("125.50"),
                        "INR",
                        "Admin monitor test"));

        var result =
                service.search(
                        new AdminTransactionSearchFilter(
                                "monitor-0001",
                                101L,
                                7L,
                                TransactionType.TRANSFER,
                                TransactionStatus.PENDING,
                                LocalDate.now(),
                                LocalDate.now()),
                        0,
                        20);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).reference()).isEqualTo("ADM-MONITOR-0001");
        assertThat(result.getContent().get(0).amount()).isEqualByComparingTo("125.50");
    }

    @Test
    void rejectsAnInvertedDateRange() {
        assertThatThrownBy(
                        () ->
                                new AdminTransactionSearchFilter(
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        LocalDate.of(2026, 9, 2),
                                        LocalDate.of(2026, 9, 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("start date");
    }
}
