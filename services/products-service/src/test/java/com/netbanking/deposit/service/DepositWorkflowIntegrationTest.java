package com.netbanking.deposit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.netbanking.ServiceTestBase;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netbanking.contracts.AccountSnapshot;
import com.netbanking.contracts.DepositLedgerCommand;
import com.netbanking.contracts.LedgerReceipt;
import com.netbanking.discovery.DepositLedgerClient;
import com.netbanking.discovery.LedgerClient;
import com.netbanking.discovery.OtpClient;
import com.netbanking.deposit.api.CloseDepositRequest;
import com.netbanking.deposit.api.DepositClosureChallengeRequest;
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
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicBoolean;

class DepositWorkflowIntegrationTest extends ServiceTestBase {
    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbc;
    @Autowired DepositService deposits;
    @Autowired DepositClosureService closures;
    @Autowired ObjectMapper json;
    @MockitoBean LedgerClient accounts;
    @MockitoBean DepositLedgerClient ledger;
    @MockitoBean OtpClient otp;

    @Test
    void fdAndRdRecoverFundingAndPayMaturityOnce() throws Exception {
        try (var connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/migration/V5__fixed_recurring_deposits.sql"));
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/migration/V7__deposit_early_closure.sql"));
        }
        jdbc.execute("CREATE TABLE IF NOT EXISTS event_outbox(event_id VARCHAR(36) PRIMARY KEY,destination VARCHAR(50),envelope CLOB)");
        int existingNotices = jdbc.queryForObject("SELECT COUNT(*) FROM event_outbox", Integer.class);
        when(accounts.account(7L, 9L)).thenReturn(new AccountSnapshot(9L, "1234567890",
                "INR", "ACTIVE", new BigDecimal("100000")));
        AtomicBoolean failFirst = new AtomicBoolean(true);
        AtomicBoolean failFirstClosure = new AtomicBoolean(true);
        when(ledger.post(any(DepositLedgerCommand.class))).thenAnswer(invocation -> {
            if (failFirst.getAndSet(false))
                throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE);
            DepositLedgerCommand command = invocation.getArgument(0);
            if (command.operationId().startsWith("deposit-close-")
                    && failFirstClosure.getAndSet(false))
                throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE);
            return new LedgerReceipt(1L, command.operationId(), "COMPLETED",
                    command.amount(), "INR");
        });

        var quote = deposits.quote(7L, new DepositQuoteRequest(9L, "RD",
                new BigDecimal("1000"), 6));
        assertThat(quote.expiresAt().getOffset()).isEqualTo(ZoneOffset.UTC);
        assertThat(json.valueToTree(quote).get("expiresAt").asText()).endsWith("Z");
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

        var closableFdQuote = deposits.quote(7L, new DepositQuoteRequest(9L, "FD",
                new BigDecimal("2000"), 12));
        var closableFd = deposits.open(7L, new OpenDepositRequest(closableFdQuote.quoteId(), "fd-close-key"));
        var closingQuote = closures.quote(7L, closableFd.depositId());
        assertThat(closingQuote.principal()).isEqualByComparingTo("2000");
        assertThat(closingQuote.annualRatePercent()).isEqualByComparingTo("5.50");
        assertThat(closingQuote.payoutAmount()).isGreaterThanOrEqualTo(closingQuote.principal());
        assertThatThrownBy(() -> closures.quote(8L, closableFd.depositId()))
                .isInstanceOf(com.netbanking.common.exception.ResourceNotFoundException.class);
        when(otp.issue(eq(7L), eq("DEPOSIT_CLOSURE"), anyString()))
                .thenReturn(new OtpClient.Challenge("challenge-1"));
        assertThat(closures.challenge(7L, closableFd.depositId(),
                new DepositClosureChallengeRequest(closingQuote.quoteId())).challengeId())
                .isEqualTo("challenge-1");
        var closeRequest = new CloseDepositRequest(closingQuote.quoteId(), "close-1", "challenge-1", "123456");
        assertThatThrownBy(() -> closures.close(7L, closableFd.depositId(), closeRequest))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(deposits.get(7L, closableFd.depositId()).status()).isEqualTo("CLOSURE_PENDING");
        closures.recover();
        var closedFd = closures.close(7L, closableFd.depositId(), closeRequest);
        assertThat(closedFd.status()).isEqualTo("CLOSED");
        assertThat(closures.close(7L, closableFd.depositId(), closeRequest)).isEqualTo(closedFd);
        assertThat(deposits.get(7L, closableFd.depositId()).status()).isEqualTo("CLOSED");
        assertThatThrownBy(() -> closures.close(7L, closableFd.depositId(),
                new CloseDepositRequest(closingQuote.quoteId(), "different", "challenge-1", "123456")))
                .isInstanceOf(com.netbanking.common.exception.ConflictException.class);

        var closableRdQuote = deposits.quote(7L, new DepositQuoteRequest(9L, "RD",
                new BigDecimal("500"), 12));
        var closableRd = deposits.open(7L, new OpenDepositRequest(closableRdQuote.quoteId(), "rd-close-key"));
        var rdClosingQuote = closures.quote(7L, closableRd.depositId());
        var closedRd = closures.close(7L, closableRd.depositId(),
                new CloseDepositRequest(rdClosingQuote.quoteId(), "close-2", "challenge-1", "123456"));
        assertThat(closedRd.status()).isEqualTo("CLOSED");
        assertThat(closedRd.payoutAmount()).isGreaterThanOrEqualTo(new BigDecimal("500"));
        verify(otp, times(2)).authorize(anyString(), eq(7L), eq("challenge-1"), eq("123456"),
                eq("DEPOSIT_CLOSURE"), anyString());

        var overdueQuote = deposits.quote(7L, new DepositQuoteRequest(9L, "RD",
                new BigDecimal("300"), 12));
        var overdue = deposits.open(7L, new OpenDepositRequest(overdueQuote.quoteId(), "rd-overdue-key"));
        jdbc.update("UPDATE deposit_contract SET next_due_at = ? WHERE deposit_id = ?",
                Timestamp.valueOf(LocalDateTime.now(ZoneOffset.UTC).minusDays(1)), overdue.depositId());
        assertThatThrownBy(() -> closures.quote(7L, overdue.depositId()))
                .isInstanceOf(com.netbanking.common.exception.ConflictException.class);
        deposits.payDueInstallment(7L, overdue.depositId());
        var fresh = closures.quote(7L, overdue.depositId());
        assertThat(fresh.principal()).isEqualByComparingTo("600");
        jdbc.update("UPDATE deposit_closure_quote SET expires_at = ? WHERE closure_quote_id = ?",
                Timestamp.valueOf(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1)), fresh.quoteId());
        assertThatThrownBy(() -> closures.challenge(7L, overdue.depositId(),
                new DepositClosureChallengeRequest(fresh.quoteId())))
                .isInstanceOf(com.netbanking.common.exception.ConflictException.class);
    }
}
