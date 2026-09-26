package com.netbanking.ledger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.netbanking.ServiceTestBase;
import com.netbanking.contracts.*;
import com.netbanking.discovery.CustomerDirectory;
import com.netbanking.ledger.service.LedgerService;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.concurrent.*;

class LedgerIntegrationTest extends ServiceTestBase {
    @Autowired JdbcTemplate jdbc;
    @Autowired LedgerService ledger;
    @MockitoBean CustomerDirectory customers;

    @BeforeEach
    void seed() {
        jdbc.execute(
                "CREATE TABLE IF NOT EXISTS ledger_operation(caller VARCHAR(50),operation_id"
                        + " VARCHAR(64),request_fingerprint VARCHAR(64),receipt CLOB,PRIMARY"
                        + " KEY(caller,operation_id))");
        jdbc.execute(
                "CREATE TABLE IF NOT EXISTS event_outbox(event_id VARCHAR(36) PRIMARY"
                        + " KEY,destination VARCHAR(50),envelope CLOB)");
        for (String table :
                new String[] {
                    "event_outbox",
                    "ledger_operation",
                    "account_transaction_entry",
                    "bank_transaction_status_history",
                    "bank_transaction",
                    "account_holder",
                    "bank_account",
                    "branch"
                }) jdbc.update("DELETE FROM " + table);
        jdbc.update(
                "INSERT INTO"
                    + " branch(branch_id,bank_id,branch_code,branch_name,ifsc_code,city,state,is_active)"
                    + " VALUES(1,1,'MAIN','Main','ABCD0001234','City','State','Y')");
        for (int id = 1; id <= 2; id++) {
            jdbc.update(
                    "INSERT INTO"
                        + " bank_account(account_id,branch_id,account_number,account_type,currency_code,account_status,current_balance,available_balance)"
                        + " VALUES(?,1,?,'SAVINGS','INR','ACTIVE',1000,1000)",
                    id,
                    "123456789" + id);
            jdbc.update(
                    "INSERT INTO account_holder(account_id,customer_id,is_active,holder_type)"
                            + " VALUES(?,11,'Y','PRIMARY')",
                    id);
        }
        when(customers.requireCustomerIdForUser(7L)).thenReturn(11L);
    }

    LedgerCommand command(String key, Long source, String destination, String amount) {
        return new LedgerCommand(
                key,
                7L,
                source,
                destination,
                "ABCD0001234",
                "TRANSFER",
                new BigDecimal(amount),
                "INR",
                "Test");
    }

    BigDecimal balance(long id) {
        return jdbc.queryForObject(
                "SELECT current_balance FROM bank_account WHERE account_id = ?",
                BigDecimal.class,
                id);
    }

    @Test
    void commitsBothBalancesEntriesAndOutboxOnce() {
        var request = command("transfer-1", 1L, "1234567892", "100.1234");
        var first = ledger.post("payments-service", request);
        assertThat(ledger.post("payments-service", request)).isEqualTo(first);
        assertThat(balance(1)).isEqualByComparingTo("899.8766");
        assertThat(balance(2)).isEqualByComparingTo("1100.1234");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM account_transaction_entry", Integer.class))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM event_outbox", Integer.class))
                .isEqualTo(1);
        assertThatThrownBy(
                        () ->
                                ledger.post(
                                        "payments-service",
                                        command("transfer-1", 1L, "1234567892", "101")))
                .isInstanceOf(com.netbanking.common.exception.ConflictException.class);
    }

    @Test
    void failureAfterDebitRollsBackTheEntireLedgerTransaction() {
        jdbc.update("UPDATE bank_account SET account_status='FROZEN' WHERE account_id=2");
        assertThatThrownBy(
                        () ->
                                ledger.post(
                                        "payments-service",
                                        command("rollback", 1L, "1234567892", "100")))
                .isInstanceOf(IllegalStateException.class);
        assertThat(balance(1)).isEqualByComparingTo("1000");
        assertThat(balance(2)).isEqualByComparingTo("1000");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bank_transaction", Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ledger_operation", Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM event_outbox", Integer.class))
                .isZero();
    }

    @Test
    void oppositeDirectionTransfersAndDuplicateRequestsPreserveBalances() throws Exception {
        var pool = Executors.newFixedThreadPool(4);
        var start = new CountDownLatch(1);
        try {
            var forward = command("forward", 1L, "1234567892", "100");
            var reverse = command("reverse", 2L, "1234567891", "40");
            var tasks =
                    java.util.List.of(forward, forward, reverse, reverse).stream()
                            .map(
                                    c ->
                                            pool.submit(
                                                    () -> {
                                                        start.await();
                                                        return ledger.post("payments-service", c);
                                                    }))
                            .toList();
            start.countDown();
            for (var task : tasks) task.get(15, TimeUnit.SECONDS);
            assertThat(balance(1)).isEqualByComparingTo("940");
            assertThat(balance(2)).isEqualByComparingTo("1060");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bank_transaction", Integer.class))
                    .isEqualTo(2);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void selfTransferAndUnknownExternalDestinationCannotDebit() {
        assertThatThrownBy(
                        () ->
                                ledger.post(
                                        "payments-service",
                                        command("self", 1L, "1234567891", "100")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                ledger.post(
                                        "payments-service",
                                        command("external", 1L, "9999999999", "100")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(balance(1)).isEqualByComparingTo("1000");
    }
}
