package com.netbanking.loan;

import static org.assertj.core.api.Assertions.*;

import com.netbanking.ServiceTestBase;
import com.netbanking.contracts.*;
import com.netbanking.loan.api.*;
import com.netbanking.loan.domain.*;
import com.netbanking.loan.repository.*;
import com.netbanking.loan.service.LoanRepaymentStore;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;

class LoanRepaymentIntegrationTest extends ServiceTestBase {
    @Autowired LoanRepository loans;
    @Autowired LoanPaymentRepository payments;
    @Autowired LoanRepaymentStore store;
    @Autowired JdbcTemplate jdbc;
    Long loanId;

    @BeforeEach
    void seed() {
        jdbc.execute(
                "CREATE TABLE IF NOT EXISTS loan_repayment_operation(operation_key VARCHAR(64)"
                    + " PRIMARY KEY,loan_id BIGINT,customer_id BIGINT,request_key"
                    + " VARCHAR(64),request_fingerprint VARCHAR(64),amount"
                    + " DECIMAL(19,4),ledger_command CLOB,state VARCHAR(20),created_at TIMESTAMP"
                    + " DEFAULT CURRENT_TIMESTAMP,UNIQUE(loan_id,request_key))");
        jdbc.execute(
                "CREATE TABLE IF NOT EXISTS event_outbox(event_id VARCHAR(36) PRIMARY"
                        + " KEY,destination VARCHAR(50),envelope CLOB)");
        jdbc.update("DELETE FROM loan_repayment_operation");
        jdbc.update("DELETE FROM event_outbox");
        payments.deleteAll();
        loans.deleteAll();
        loanId =
                loans.saveAndFlush(
                                new Loan(
                                        21L,
                                        "LN20260001",
                                        LoanType.PERSONAL,
                                        new BigDecimal("1000"),
                                        new BigDecimal("1000"),
                                        new BigDecimal("10"),
                                        12,
                                        new BigDecimal("100"),
                                        "INR",
                                        LocalDate.now(),
                                        LocalDate.now().plusMonths(1),
                                        LocalDate.now().plusYears(1),
                                        LoanStatus.ACTIVE))
                        .getLoanId();
    }

    LoanRepaymentStore.Operation reserve(String key, String amount) {
        return store.prepare(
                7L,
                21L,
                loanId,
                new CreateLoanPaymentRequest(8L, new BigDecimal(amount), key),
                new AccountSnapshot(8L, "1234567890", "INR", "ACTIVE", new BigDecimal("2000")));
    }

    @Test
    void reservationPreventsOverpaymentAndDefinitiveFailureReleasesIt() {
        var first = reserve("payment-1", "900");
        assertThatThrownBy(() -> reserve("payment-2", "200"))
                .isInstanceOf(com.netbanking.common.exception.ConflictException.class);
        store.failed(first);
        assertThat(reserve("payment-2", "200").state()).isEqualTo("RESERVED");
        assertThat(loans.findById(loanId).orElseThrow().getOutstandingPrincipal())
                .isEqualByComparingTo("1000");
    }

    @Test
    void completionAndItsRetryApplyPrincipalAndPublishEventsOnce() {
        var operation = reserve("payment-1", "200");
        var receipt = new LedgerReceipt(55L, "TXN-55", "COMPLETED", new BigDecimal("200"), "INR");
        var first = store.complete(operation, receipt);
        var second = store.complete(operation, receipt);
        assertThat(second.loanPaymentId()).isEqualTo(first.loanPaymentId());
        assertThat(loans.findById(loanId).orElseThrow().getOutstandingPrincipal())
                .isEqualByComparingTo("800");
        assertThat(payments.count()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM event_outbox", Integer.class))
                .isEqualTo(2);
    }

    @Test
    void failureAfterLedgerSuccessCanRetryLocalFinalization() {
        var operation = reserve("payment-1", "200");
        var receipt = new LedgerReceipt(55L, "TXN-55", "COMPLETED", new BigDecimal("200"), "INR");
        jdbc.execute(
                "ALTER TABLE event_outbox ADD CONSTRAINT reject_events CHECK(destination ="
                        + " 'impossible')");
        try {
            assertThatThrownBy(() -> store.complete(operation, receipt))
                    .isInstanceOf(RuntimeException.class);
        } finally {
            jdbc.execute("ALTER TABLE event_outbox DROP CONSTRAINT reject_events");
        }
        assertThat(store.find(loanId, "payment-1").state()).isEqualTo("RESERVED");
        assertThat(loans.findById(loanId).orElseThrow().getOutstandingPrincipal())
                .isEqualByComparingTo("1000");
        assertThat(payments.count()).isZero();
        store.complete(operation, receipt);
        assertThat(loans.findById(loanId).orElseThrow().getOutstandingPrincipal())
                .isEqualByComparingTo("800");
    }
}
