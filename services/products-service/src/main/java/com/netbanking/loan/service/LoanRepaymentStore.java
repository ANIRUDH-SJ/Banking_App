package com.netbanking.loan.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netbanking.common.exception.*;
import com.netbanking.contracts.*;
import com.netbanking.events.NotificationPublisher;
import com.netbanking.loan.api.*;
import com.netbanking.loan.domain.*;
import com.netbanking.loan.repository.*;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class LoanRepaymentStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final LoanRepository loans;
    private final LoanPaymentRepository payments;
    private final LoanPaymentAuditWriter audit;
    private final NotificationPublisher notifications;

    public LoanRepaymentStore(
            JdbcTemplate jdbc,
            ObjectMapper json,
            LoanRepository loans,
            LoanPaymentRepository payments,
            LoanPaymentAuditWriter audit,
            NotificationPublisher notifications) {
        this.jdbc = jdbc;
        this.json = json;
        this.loans = loans;
        this.payments = payments;
        this.audit = audit;
        this.notifications = notifications;
    }

    public record Operation(
            String key,
            Long loanId,
            Long customerId,
            String requestKey,
            String fingerprint,
            String state,
            LedgerCommand command) {}

    private Operation map(java.sql.ResultSet rs, int index) throws java.sql.SQLException {
        try {
            return new Operation(
                    rs.getString("operation_key"),
                    rs.getLong("loan_id"),
                    rs.getLong("customer_id"),
                    rs.getString("request_key"),
                    rs.getString("request_fingerprint"),
                    rs.getString("state"),
                    json.readValue(rs.getString("ledger_command"), LedgerCommand.class));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    public Operation find(Long loanId, String key) {
        return jdbc
                .query(
                        "SELECT * FROM loan_repayment_operation WHERE loan_id = ? AND request_key ="
                                + " ?",
                        this::map,
                        loanId,
                        key)
                .stream()
                .findFirst()
                .orElse(null);
    }

    @Transactional
    public Operation prepare(
            Long userId,
            Long customerId,
            Long loanId,
            CreateLoanPaymentRequest request,
            AccountSnapshot account) {
        Loan loan =
                loans.findByLoanIdAndCustomerIdForUpdate(loanId, customerId)
                        .orElseThrow(() -> new ResourceNotFoundException("Loan was not found."));
        String fingerprint =
                LoanPaymentIntent.fingerprint(
                        userId, loanId, request.sourceAccountId(), request.amount());
        Operation existing = find(loanId, request.idempotencyKey());
        if (existing != null) {
            if (!fingerprint.equals(existing.fingerprint()))
                throw new ConflictException(
                        "Idempotency key was used for different repayment details.");
            return existing;
        }
        if (loan.getLoanStatus() != LoanStatus.ACTIVE)
            throw new ConflictException("Loan must be active.");
        if (!"ACTIVE".equals(account.status())
                || !loan.getCurrencyCode().trim().equals(account.currencyCode()))
            throw new IllegalArgumentException(
                    "An active source account with the loan currency is required.");
        BigDecimal reserved =
                jdbc.queryForObject(
                        "SELECT COALESCE(SUM(amount), 0) FROM loan_repayment_operation WHERE"
                                + " loan_id = ? AND state = 'RESERVED'",
                        BigDecimal.class,
                        loanId);
        if (request.amount().signum() <= 0
                || request.amount().compareTo(loan.getOutstandingPrincipal().subtract(reserved))
                        > 0)
            throw new ConflictException(
                    "Amount exceeds principal available for repayment, including pending"
                            + " repayments.");
        var command =
                new LedgerCommand(
                        UUID.randomUUID().toString(),
                        userId,
                        request.sourceAccountId(),
                        null,
                        null,
                        "LOAN_PAYMENT",
                        request.amount(),
                        loan.getCurrencyCode().trim(),
                        "Loan payment " + loan.getLoanAccountNumber());
        try {
            jdbc.update(
                    "INSERT INTO loan_repayment_operation (operation_key, loan_id, customer_id,"
                        + " request_key, request_fingerprint, amount, ledger_command, state) VALUES"
                        + " (?, ?, ?, ?, ?, ?, ?, 'RESERVED')",
                    command.operationId(),
                    loanId,
                    customerId,
                    request.idempotencyKey(),
                    fingerprint,
                    request.amount(),
                    json.writeValueAsString(command));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalArgumentException(e);
        }
        return find(loanId, request.idempotencyKey());
    }

    @Transactional
    public LoanPaymentResponse complete(Operation operation, LedgerReceipt receipt) {
        Loan loan =
                loans.findByLoanIdAndCustomerIdForUpdate(operation.loanId(), operation.customerId())
                        .orElseThrow();
        Operation current = find(operation.loanId(), operation.requestKey());
        if ("COMPLETED".equals(current.state())) return receipt(current);
        if (!"RESERVED".equals(current.state()))
            throw new ConflictException("Repayment is not reserved.");
        if (!"COMPLETED".equals(receipt.status())
                || receipt.amount().compareTo(current.command().amount()) != 0
                || !receipt.currencyCode().equals(current.command().currencyCode()))
            throw new IllegalStateException("Ledger receipt does not match repayment.");
        loan.applyPayment(current.command().amount());
        LoanPayment payment =
                payments.saveAndFlush(
                        new LoanPayment(
                                current.loanId(),
                                current.command().sourceAccountId(),
                                receipt.transactionId(),
                                receipt.reference(),
                                current.requestKey(),
                                receipt.amount(),
                                receipt.currencyCode(),
                                loan.getOutstandingPrincipal(),
                                current.fingerprint()));
        jdbc.update(
                "UPDATE loan_repayment_operation SET state = 'COMPLETED' WHERE operation_key = ?",
                current.key());
        audit.recordCompleted(current.command().userId(), current.loanId(), receipt.reference());
        notifications.publish(
                current.command().userId(),
                "Loan repayment completed",
                "Repayment completed. Reference: " + receipt.reference());
        return LoanPaymentService.toResponse(payment);
    }

    public LoanPaymentResponse receipt(Operation operation) {
        return LoanPaymentService.toResponse(
                payments.findByLoanIdAndIdempotencyKey(operation.loanId(), operation.requestKey())
                        .orElseThrow());
    }

    @Transactional
    public void failed(Operation operation) {
        loans.findByLoanIdAndCustomerIdForUpdate(operation.loanId(), operation.customerId())
                .orElseThrow();
        jdbc.update(
                "UPDATE loan_repayment_operation SET state = 'FAILED' WHERE operation_key = ? AND"
                        + " state = 'RESERVED'",
                operation.key());
    }

    public void defer(String key) {
        jdbc.update(
                "UPDATE loan_repayment_operation SET next_attempt_at = ? WHERE operation_key = ?",
                java.sql.Timestamp.from(java.time.Instant.now().plusSeconds(30)),
                key);
    }

    public List<Operation> recoverable() {
        return jdbc.query(
                "SELECT * FROM loan_repayment_operation WHERE state = 'RESERVED' AND"
                        + " next_attempt_at <= CURRENT_TIMESTAMP ORDER BY created_at FETCH FIRST 50"
                        + " ROWS ONLY",
                this::map);
    }
}
