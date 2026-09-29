package com.netbanking.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netbanking.audit.service.AuditLogService;
import com.netbanking.biller.provider.BillerPaymentReceipt;
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
            Long beneficiaryId,
            Long billerId,
            String billerCode,
            String billReference,
            String narration) {}

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

    public Details details(String operationKey) {
        String value =
                jdbc.queryForObject(
                        "SELECT payment_details FROM payment_operation WHERE operation_key = ?",
                        String.class,
                        operationKey);
        try {
            Details details = json.readValue(value, Details.class);
            if (details.billerId() == null
                    || (details.billerCode() != null && !details.billerCode().isBlank())) {
                return details;
            }
            String billerCode =
                    jdbc.queryForObject(
                            "SELECT biller_code FROM biller WHERE biller_id = ?",
                            String.class,
                            details.billerId());
            return new Details(
                    details.beneficiaryId(),
                    details.billerId(),
                    billerCode,
                    details.billReference(),
                    details.narration());
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
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
    public PaymentReceiptResponse debited(String key, LedgerReceipt receipt) {
        Operation current = locked(key);
        if (java.util.Set.of("DEBITED", "COMPLETED", "REVERSED").contains(current.state())) {
            requireSameLedgerReceipt(current, receipt);
            return current.receipt();
        }
        if (!"BILL_PAYMENT".equals(current.kind()) || !"AUTHORIZED".equals(current.state())) {
            throw new ConflictException("Bill payment is not authorized for debit.");
        }
        requireMatchingReceipt(current, receipt);
        jdbc.update(
                "UPDATE payment_operation SET state = 'DEBITED', transaction_id = ?,"
                        + " transaction_reference = ? WHERE operation_key = ?",
                receipt.transactionId(),
                receipt.reference(),
                key);
        return get(key).receipt();
    }

    @Transactional
    public PaymentReceiptResponse complete(String key, LedgerReceipt receipt) {
        Operation current = locked(key);
        if ("COMPLETED".equals(current.state())) return current.receipt();
        if (!"TRANSFER".equals(current.kind()) || !"AUTHORIZED".equals(current.state()))
            throw new ConflictException("Payment is not authorized.");
        requireMatchingReceipt(current, receipt);
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

    @Transactional
    public PaymentReceiptResponse completeBill(
            String key, LedgerReceipt debit, BillerPaymentReceipt provider) {
        Operation current = locked(key);
        if ("COMPLETED".equals(current.state())) return current.receipt();
        if (!"BILL_PAYMENT".equals(current.kind()) || !"DEBITED".equals(current.state())) {
            throw new ConflictException("Bill payment is not ready for collection.");
        }
        requireSameLedgerReceipt(current, debit);
        if (!provider.accepted()) {
            throw new IllegalArgumentException("Biller receipt is not accepted.");
        }
        jdbc.update(
                "UPDATE payment_operation SET state = 'COMPLETED', provider_reference = ?,"
                        + " provider_status = ? WHERE operation_key = ?",
                provider.providerReference(),
                provider.status(),
                key);
        recordOutcome(current, debit, "COMPLETED", provider.providerReference());
        return get(key).receipt();
    }

    @Transactional
    public PaymentReceiptResponse reversed(
            String key,
            LedgerReceipt debit,
            LedgerReceipt reversal,
            BillerPaymentReceipt provider) {
        Operation current = locked(key);
        if ("REVERSED".equals(current.state())) return current.receipt();
        if (!"BILL_PAYMENT".equals(current.kind()) || !"DEBITED".equals(current.state())) {
            throw new ConflictException("Bill payment is not ready for reversal.");
        }
        requireSameLedgerReceipt(current, debit);
        requireMatchingReceipt(current, reversal);
        if (!provider.rejected()) {
            throw new IllegalArgumentException("Biller receipt is not rejected.");
        }
        jdbc.update(
                "UPDATE payment_operation SET state = 'REVERSED', provider_reference = ?,"
                    + " provider_status = ?, reversal_transaction_id = ?,"
                    + " reversal_transaction_reference = ? WHERE operation_key = ?",
                provider.providerReference(),
                provider.status(),
                reversal.transactionId(),
                reversal.reference(),
                key);
        recordOutcome(current, debit, "REVERSED", provider.providerReference());
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
                "SELECT * FROM payment_operation WHERE state IN ('AUTHORIZED','DEBITED') AND"
                    + " next_attempt_at <= CURRENT_TIMESTAMP ORDER BY created_at FETCH FIRST 50"
                    + " ROWS ONLY",
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

    private Operation locked(String key) {
        return jdbc.query(
                        "SELECT * FROM payment_operation WHERE operation_key = ? FOR UPDATE",
                        this::map,
                        key)
                .stream()
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Payment was not found."));
    }

    private static void requireMatchingReceipt(Operation current, LedgerReceipt receipt) {
        if (!"COMPLETED".equals(receipt.status())
                || receipt.amount().compareTo(current.command().amount()) != 0
                || !receipt.currencyCode().equals(current.command().currencyCode())) {
            throw new IllegalStateException("Ledger receipt does not match the payment.");
        }
    }

    private static void requireSameLedgerReceipt(Operation current, LedgerReceipt receipt) {
        requireMatchingReceipt(current, receipt);
        if (!receipt.transactionId().equals(current.transactionId())
                || !receipt.reference().equals(current.reference())) {
            throw new IllegalStateException("Ledger receipt changed during payment recovery.");
        }
    }

    private void recordOutcome(
            Operation current, LedgerReceipt receipt, String outcome, String providerReference) {
        audit.record(
                current.command().userId(),
                current.kind() + "_" + outcome,
                "PAYMENT",
                current.key(),
                "SUCCESS",
                "transactionReference="
                        + receipt.reference()
                        + ", providerReference="
                        + providerReference);
        notifications.publish(
                current.command().userId(),
                "Payment " + outcome.toLowerCase(java.util.Locale.ROOT),
                "Payment of "
                        + receipt.amount()
                        + " "
                        + receipt.currencyCode()
                        + " was "
                        + outcome.toLowerCase(java.util.Locale.ROOT)
                        + ". Reference: "
                        + receipt.reference());
    }
}
