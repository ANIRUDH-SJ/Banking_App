package com.netbanking.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netbanking.audit.service.AuditLogService;
import com.netbanking.common.exception.*;
import com.netbanking.contracts.*;
import com.netbanking.events.NotificationPublisher;
import com.netbanking.payment.api.PaymentReceiptResponse;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PaymentWorkflowStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final AuditLogService audit;
    private final NotificationPublisher notifications;

    public PaymentWorkflowStore(
            JdbcTemplate jdbc,
            ObjectMapper json,
            AuditLogService audit,
            NotificationPublisher notifications) {
        this.jdbc = jdbc;
        this.json = json;
        this.audit = audit;
        this.notifications = notifications;
    }

    public record Details(
            Long beneficiaryId, Long billerId, String billReference, String narration) {}

    public record Operation(
            Long id,
            String kind,
            String key,
            String fingerprint,
            String digest,
            String state,
            LedgerCommand command,
            Long transactionId,
            String reference) {
        public PaymentReceiptResponse receipt() {
            return new PaymentReceiptResponse(
                    id, transactionId, reference, state, command.amount(), command.currencyCode());
        }
    }

    private Operation map(java.sql.ResultSet rs, int index) throws java.sql.SQLException {
        try {
            return new Operation(
                    rs.getLong("payment_id"),
                    rs.getString("kind"),
                    rs.getString("operation_key"),
                    rs.getString("request_fingerprint"),
                    rs.getString("intent_digest"),
                    rs.getString("state"),
                    json.readValue(rs.getString("ledger_command"), LedgerCommand.class),
                    rs.getObject("transaction_id", Long.class),
                    rs.getString("transaction_reference"));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    public Operation find(Long userId, String kind, String requestKey) {
        return jdbc
                .query(
                        "SELECT * FROM payment_operation WHERE user_id = ? AND kind = ? AND"
                                + " request_key = ?",
                        this::map,
                        userId,
                        kind,
                        requestKey)
                .stream()
                .findFirst()
                .orElse(null);
    }

    public Operation get(String operationKey) {
        return jdbc
                .query(
                        "SELECT * FROM payment_operation WHERE operation_key = ?",
                        this::map,
                        operationKey)
                .stream()
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Payment was not found."));
    }

    @Transactional
    public Operation create(
            String kind,
            String requestKey,
            String fingerprint,
            String digest,
            LedgerCommand command,
            Details details) {
        try {
            jdbc.update(
                    "INSERT INTO payment_operation (kind, operation_key, user_id, request_key,"
                        + " request_fingerprint, intent_digest, ledger_command, payment_details,"
                        + " state) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'AWAITING_OTP')",
                    kind,
                    command.operationId(),
                    command.userId(),
                    requestKey,
                    fingerprint,
                    digest,
                    json.writeValueAsString(command),
                    json.writeValueAsString(details));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalArgumentException(e);
        }
        return get(command.operationId());
    }

    @Transactional
    public void authorized(String key) {
        jdbc.update(
                "UPDATE payment_operation SET state = 'AUTHORIZED' WHERE operation_key = ? AND"
                        + " state = 'AWAITING_OTP'",
                key);
    }

    @Transactional
    public void failed(String key) {
        jdbc.update(
                "UPDATE payment_operation SET state = 'FAILED' WHERE operation_key = ? AND state ="
                        + " 'AUTHORIZED'",
                key);
    }

    @Transactional
    public PaymentReceiptResponse complete(String key, LedgerReceipt receipt) {
        Operation current =
                jdbc.query(
                                "SELECT * FROM payment_operation WHERE operation_key = ? FOR"
                                        + " UPDATE",
                                this::map,
                                key)
                        .get(0);
        if ("COMPLETED".equals(current.state())) return current.receipt();
        if (!"AUTHORIZED".equals(current.state()))
            throw new ConflictException("Payment is not authorized.");
        if (!"COMPLETED".equals(receipt.status())
                || receipt.amount().compareTo(current.command().amount()) != 0
                || !receipt.currencyCode().equals(current.command().currencyCode()))
            throw new IllegalStateException("Ledger receipt does not match the payment.");
        jdbc.update(
                "UPDATE payment_operation SET state = 'COMPLETED', transaction_id = ?,"
                        + " transaction_reference = ? WHERE operation_key = ?",
                receipt.transactionId(),
                receipt.reference(),
                key);
        audit.record(
                current.command().userId(),
                current.kind() + "_COMPLETED",
                "PAYMENT",
                key,
                "SUCCESS",
                "transactionReference=" + receipt.reference());
        notifications.publish(
                current.command().userId(),
                "Payment completed",
                "Payment of "
                        + receipt.amount()
                        + " "
                        + receipt.currencyCode()
                        + " completed. Reference: "
                        + receipt.reference());
        return get(key).receipt();
    }

    public void defer(String key) {
        jdbc.update(
                "UPDATE payment_operation SET next_attempt_at = ? WHERE operation_key = ?",
                java.sql.Timestamp.from(java.time.Instant.now().plusSeconds(30)),
                key);
    }

    public List<Operation> recoverable() {
        return jdbc.query(
                "SELECT * FROM payment_operation WHERE state = 'AUTHORIZED' AND next_attempt_at <="
                        + " CURRENT_TIMESTAMP ORDER BY created_at FETCH FIRST 50 ROWS ONLY",
                this::map);
    }

    public List<PaymentReceiptResponse> history(Long userId, String kind) {
        return jdbc
                .query(
                        "SELECT * FROM payment_operation WHERE user_id = ? AND kind = ? ORDER BY"
                                + " created_at DESC FETCH FIRST 100 ROWS ONLY",
                        this::map,
                        userId,
                        kind)
                .stream()
                .map(Operation::receipt)
                .toList();
    }
}
