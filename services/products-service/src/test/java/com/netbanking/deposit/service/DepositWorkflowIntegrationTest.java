package com.netbanking.deposit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.netbanking.ServiceTestBase;
import com.netbanking.contracts.AccountSnapshot;
import com.netbanking.contracts.DepositLedgerCommand;
import com.netbanking.contracts.LedgerReceipt;
import com.netbanking.discovery.DepositLedgerClient;
import com.netbanking.discovery.LedgerClient;
import com.netbanking.deposit.api.DepositQuoteRequest;
import com.netbanking.deposit.api.OpenDepositRequest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;

class DepositWorkflowIntegrationTest extends ServiceTestBase {
    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbc;
    @Autowired DepositService deposits;
    @MockitoBean LedgerClient accounts;
    @MockitoBean DepositLedgerClient ledger;

    @Test
    void fdAndRdRecoverFundingAndPayMaturityOnce() throws Exception {
        try (var connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/migration/V5__fixed_recurring_deposits.sql"));
        }
        jdbc.execute("CREATE TABLE IF NOT EXISTS event_outbox(event_id VARCHAR(36) PRIMARY KEY,destination VARCHAR(50),envelope CLOB)");
        int existingNotices = jdbc.queryForObject("SELECT COUNT(*) FROM event_outbox", Integer.class);
        when(accounts.account(7L, 9L)).thenReturn(new AccountSnapshot(9L, "1234567890",
                "INR", "ACTIVE", new BigDecimal("100000")));
        AtomicBoolean failFirst = new AtomicBoolean(true);
        when(ledger.post(any(DepositLedgerCommand.class))).thenAnswer(invocation -> {
            if (failFirst.getAndSet(false))
                throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE);
            DepositLedgerCommand command = invocation.getArgument(0);
            return new LedgerReceipt(1L, command.operationId(), "COMPLETED",
                    command.amount(), "INR");
        });

        var quote = deposits.quote(7L, new DepositQuoteRequest(9L, "RD",
                new BigDecimal("1000"), 6));
        assertThatThrownBy(() -> deposits.open(7L, new OpenDepositRequest(quote.quoteId(), "rd-key")))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(jdbc.queryForObject("SELECT status FROM deposit_contract WHERE quote_id = ?",
                String.class, quote.quoteId())).isEqualTo("PENDING");
        var opened = deposits.open(7L, new OpenDepositRequest(quote.quoteId(), "rd-key"));
        assertThat(opened.status()).isEqualTo("ACTIVE");
        assertThat(deposits.open(7L, new OpenDepositRequest(quote.quoteId(), "rd-key"))
                .depositId()).isEqualTo(opened.depositId());
        assertThat(opened.installmentsPaid()).isEqualTo(1);
        assertThat(deposits.list(7L, 0, 20)).hasSize(1);

        LocalDateTime old = LocalDateTime.now().minusMonths(7);
        jdbc.update("UPDATE deposit_contract SET opened_at = ?, maturity_at = ?, next_due_at = ? WHERE deposit_id = ?",
                Timestamp.valueOf(old), Timestamp.valueOf(LocalDateTime.now().minusDays(1)),
                Timestamp.valueOf(old.plusMonths(1)), opened.depositId());
        for (int n = 0; n < 5; n++) deposits.recover();
        var matured = deposits.get(7L, opened.depositId());
        assertThat(matured.status()).isEqualTo("MATURED");
        assertThat(matured.installmentsPaid()).isEqualTo(6);
        assertThat(matured.payoutAmount()).isEqualByComparingTo("6000");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM deposit_installment WHERE deposit_id = ?",
                Integer.class, opened.depositId())).isEqualTo(6);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM event_outbox", Integer.class))
                .isEqualTo(existingNotices + 7);
        deposits.recover();
        verify(ledger, times(8)).post(any(DepositLedgerCommand.class));

        var fdQuote = deposits.quote(7L, new DepositQuoteRequest(9L, "FD",
                new BigDecimal("10000"), 12));
        var fd = deposits.open(7L, new OpenDepositRequest(fdQuote.quoteId(), "fd-key"));
        assertThat(fd.status()).isEqualTo("ACTIVE");
        assertThat(fd.installmentsPaid()).isEqualTo(1);
        jdbc.update("UPDATE deposit_contract SET maturity_at = ? WHERE deposit_id = ?",
                Timestamp.valueOf(LocalDateTime.now().minusDays(1)), fd.depositId());
        deposits.recover();
        assertThat(deposits.get(7L, fd.depositId()).status()).isEqualTo("MATURED");
        deposits.recover();
        verify(ledger, times(10)).post(any(DepositLedgerCommand.class));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM event_outbox", Integer.class))
                .isEqualTo(existingNotices + 9);
    }
}
