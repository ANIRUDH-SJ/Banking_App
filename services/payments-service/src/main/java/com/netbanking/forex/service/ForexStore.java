package com.netbanking.forex.service;

import com.netbanking.audit.service.AuditLogService;
import com.netbanking.common.exception.ConflictException;
import com.netbanking.common.exception.ResourceNotFoundException;
import com.netbanking.contracts.ForexLedgerReceipt;
import com.netbanking.events.NotificationPublisher;
import com.netbanking.forex.api.ForexConversionResponse;
import com.netbanking.forex.api.ForexQuoteResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class ForexStore {
    private final JdbcTemplate jdbc;
    private final AuditLogService audit;
    private final NotificationPublisher notifications;

    public ForexStore(JdbcTemplate jdbc, AuditLogService audit, NotificationPublisher notifications) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.notifications = notifications;
    }

    public record Quote(ForexQuoteResponse response, Long userId, LocalDateTime createdAt) {
        public String id() { return response.quoteId(); }
    }

    public record Conversion(
            Long id, Quote quote, String requestKey, String fingerprint,
            String operationId, String state, String debitReference, String creditReference) {
        public ForexConversionResponse response() {
            var q = quote.response();
            return new ForexConversionResponse(id, q.quoteId(), state, q.sourceAmount(),
                    q.sourceCurrency(), q.destinationAmount(), q.destinationCurrency(),
                    debitReference, creditReference);
        }
    }

    @Transactional
    public Quote saveQuote(Quote quote) {
        var q = quote.response();
        jdbc.update("INSERT INTO forex_quote (quote_id,user_id,source_account_id,destination_account_id,"
                        + "source_currency,destination_currency,source_amount,destination_amount,exchange_rate,created_at,expires_at)"
                        + " VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                q.quoteId(), quote.userId(), q.sourceAccountId(), q.destinationAccountId(),
                q.sourceCurrency(), q.destinationCurrency(), q.sourceAmount(), q.destinationAmount(),
                q.exchangeRate(), quote.createdAt(), q.expiresAt());
        return quote;
    }

    public Quote quote(Long userId, String quoteId) {
        return jdbc.query("SELECT * FROM forex_quote WHERE quote_id = ? AND user_id = ?",
                this::mapQuote, quoteId, userId).stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Forex quote was not found."));
    }

    private Quote mapQuote(ResultSet rs, int row) throws SQLException {
        return new Quote(new ForexQuoteResponse(rs.getString("quote_id"),
                rs.getLong("source_account_id"), rs.getLong("destination_account_id"),
                rs.getString("source_currency").trim(), rs.getString("destination_currency").trim(),
                rs.getBigDecimal("source_amount"), rs.getBigDecimal("destination_amount"),
                rs.getBigDecimal("exchange_rate"), "DEMO_CONFIGURED",
                rs.getTimestamp("expires_at").toLocalDateTime()),
                rs.getLong("user_id"), rs.getTimestamp("created_at").toLocalDateTime());
    }

    private Conversion mapConversion(ResultSet rs, int row) throws SQLException {
        Quote q = mapQuote(rs, row);
        return new Conversion(rs.getLong("conversion_id"), q, rs.getString("request_key"),
                rs.getString("request_fingerprint"), rs.getString("operation_id"),
                rs.getString("state"), rs.getString("debit_reference"),
                rs.getString("credit_reference"));
    }

    private static final String SELECT = "SELECT c.*,q.* FROM forex_conversion c JOIN forex_quote q ON q.quote_id=c.quote_id";

    public Conversion byRequest(Long userId, String requestKey) {
        return jdbc.query(SELECT + " WHERE c.user_id=? AND c.request_key=?", this::mapConversion,
                userId, requestKey).stream().findFirst().orElse(null);
    }

    public Conversion byOperation(String operationId) {
        return jdbc.query(SELECT + " WHERE c.operation_id=?", this::mapConversion, operationId)
                .stream().findFirst().orElseThrow(() -> new ResourceNotFoundException("Conversion was not found."));
    }

    @Transactional
    public Conversion create(Quote quote, String requestKey, String fingerprint, String operationId) {
        jdbc.update("INSERT INTO forex_conversion (quote_id,user_id,request_key,request_fingerprint,"
                        + "operation_id,state,created_at,next_attempt_at) VALUES (?,?,?,?,?,'AWAITING_OTP',?,?)",
                quote.id(), quote.userId(), requestKey, fingerprint, operationId,
                LocalDateTime.now(java.time.Clock.systemUTC()), LocalDateTime.now(java.time.Clock.systemUTC()));
        return byOperation(operationId);
    }

    @Transactional
    public void authorized(String operationId) {
        jdbc.update("UPDATE forex_conversion SET state='AUTHORIZED' WHERE operation_id=? AND state='AWAITING_OTP'",
                operationId);
    }

    @Transactional
    public void failed(String operationId) {
        jdbc.update("UPDATE forex_conversion SET state='FAILED' WHERE operation_id=? AND state='AUTHORIZED'",
                operationId);
    }

    @Transactional
    public ForexConversionResponse complete(String operationId, ForexLedgerReceipt receipt) {
        Conversion current = jdbc.query(SELECT + " WHERE c.operation_id=? FOR UPDATE", this::mapConversion,
                operationId).stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Conversion was not found."));
        if ("COMPLETED".equals(current.state())) return current.response();
        if (!"AUTHORIZED".equals(current.state()))
            throw new ConflictException("Conversion is not authorized.");
        var q = current.quote().response();
        if (receipt.sourceAmount().compareTo(q.sourceAmount()) != 0
                || receipt.destinationAmount().compareTo(q.destinationAmount()) != 0)
            throw new ConflictException("Ledger receipt does not match the forex quote.");
        jdbc.update("UPDATE forex_conversion SET state='COMPLETED', debit_transaction_id=?,"
                        + "debit_reference=?,credit_transaction_id=?,credit_reference=? WHERE operation_id=?",
                receipt.debitTransactionId(), receipt.debitReference(), receipt.creditTransactionId(),
                receipt.creditReference(), operationId);
        audit.record(current.quote().userId(), "FOREX_COMPLETED", "PAYMENT", operationId,
                "SUCCESS", "creditReference=" + receipt.creditReference());
        notifications.publish(current.quote().userId(), "FOREX", "Currency conversion completed",
                q.sourceAmount() + " " + q.sourceCurrency() + " converted to "
                        + q.destinationAmount() + " " + q.destinationCurrency()
                        + ". Reference: " + receipt.creditReference());
        return byOperation(operationId).response();
    }

    public List<Conversion> recoverable() {
        return jdbc.query(SELECT + " WHERE c.state='AUTHORIZED' AND c.next_attempt_at<=?"
                        + " ORDER BY c.created_at FETCH FIRST 50 ROWS ONLY",
                this::mapConversion, LocalDateTime.now(java.time.Clock.systemUTC()));
    }

    public void defer(String operationId) {
        jdbc.update("UPDATE forex_conversion SET next_attempt_at=? WHERE operation_id=? AND state='AUTHORIZED'",
                LocalDateTime.now(java.time.Clock.systemUTC()).plusSeconds(30), operationId);
    }

    public ForexConversionResponse owned(Long userId, Long conversionId) {
        return jdbc.query(SELECT + " WHERE c.user_id=? AND c.conversion_id=?", this::mapConversion,
                userId, conversionId).stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Conversion was not found.")).response();
    }

    public Page<ForexConversionResponse> history(Long userId, int page, int size) {
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM forex_conversion WHERE user_id=?",
                Long.class, userId);
        List<ForexConversionResponse> rows = jdbc.query(SELECT + " WHERE c.user_id=?"
                        + " ORDER BY c.created_at DESC,c.conversion_id DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",
                this::mapConversion, userId, (long) page * size, size).stream()
                .map(Conversion::response).toList();
        return new PageImpl<>(rows, PageRequest.of(page, size), total);
    }
}
