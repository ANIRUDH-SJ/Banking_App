package com.netbanking.deposit.service;

import com.netbanking.common.exception.ConflictException;
import com.netbanking.common.exception.ResourceNotFoundException;
import com.netbanking.contracts.DepositLedgerCommand;
import com.netbanking.contracts.RequestFingerprint;
import com.netbanking.deposit.api.*;
import com.netbanking.discovery.DepositLedgerClient;
import com.netbanking.discovery.LedgerClient;
import com.netbanking.discovery.OtpClient;
import com.netbanking.events.NotificationPublisher;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
public class DepositClosureService {
    private static final String PURPOSE = "DEPOSIT_CLOSURE";
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final LedgerClient accounts;
    private final DepositLedgerClient ledger;
    private final OtpClient otp;
    private final DepositPricing pricing;
    private final NotificationPublisher notifications;
    private final BigDecimal rateReduction;

    public DepositClosureService(
            JdbcTemplate jdbc, TransactionTemplate transactions, LedgerClient accounts,
            DepositLedgerClient ledger, OtpClient otp, DepositPricing pricing,
            NotificationPublisher notifications,
            @Value("${app.deposits.early-closure-rate-reduction:1.00}") BigDecimal rateReduction) {
        if (rateReduction.signum() < 0 || rateReduction.compareTo(new BigDecimal("5.00")) > 0)
            throw new IllegalArgumentException("Deposit closure rate reduction must be between 0 and 5 percentage points.");
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.accounts = accounts;
        this.ledger = ledger;
        this.otp = otp;
        this.pricing = pricing;
        this.notifications = notifications;
        this.rateReduction = rateReduction;
    }

    public DepositClosureQuoteResponse quote(Long userId, String depositId) {
        Contract contract = require(userId, depositId);
        requireClosable(contract);
        requireActiveAccount(contract);
        List<Installment> installments = installments(depositId);
        if (installments.size() != contract.installmentsPaid())
            throw new ConflictException("Deposit installments changed. Refresh and try again.");
        BigDecimal rate = contract.rate().subtract(rateReduction).max(BigDecimal.ZERO).setScale(4);
        BigDecimal principal = installments.stream().map(Installment::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_EVEN);
        BigDecimal interest = installments.stream().map(item -> pricing.accrued(
                item.amount(), rate, item.paidAt().toLocalDate(), now().toLocalDate()))
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_EVEN);
        LocalDateTime expiry = now().plusMinutes(10);
        String quoteId = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO deposit_closure_quote (closure_quote_id,deposit_id,user_id,"
                        + "installments_paid,principal,interest,effective_rate,payout_amount,expires_at)"
                        + " VALUES (?,?,?,?,?,?,?,?,?)",
                quoteId, depositId, userId, contract.installmentsPaid(), principal, interest,
                rate, principal.add(interest), Timestamp.valueOf(expiry));
        return new DepositClosureQuoteResponse(quoteId, depositId, principal, interest, rate,
                principal.add(interest), expiry.atOffset(ZoneOffset.UTC));
    }

    public DepositClosureChallengeResponse challenge(
            Long userId, String depositId, DepositClosureChallengeRequest request) {
        Contract contract = require(userId, depositId);
        requireClosable(contract);
        ClosureQuote quote = requireQuote(userId, depositId, request.quoteId(), contract);
        var issued = otp.issue(userId, PURPOSE, digest(quote));
        return new DepositClosureChallengeResponse(issued.challengeId(), "OTP_SENT");
    }

    public DepositClosureResponse close(Long userId, String depositId, CloseDepositRequest request) {
        Contract contract = require(userId, depositId);
        if ("CLOSED".equals(contract.status()) || "CLOSURE_PENDING".equals(contract.status())) {
            if (!request.idempotencyKey().equals(contract.closureRequestKey())
                    || !request.quoteId().equals(contract.closureQuoteId()))
                throw new ConflictException("This deposit already has a different closure request.");
            return settle(contract);
        }
        requireClosable(contract);
        requireActiveAccount(contract);
        ClosureQuote quote = requireQuote(userId, depositId, request.quoteId(), contract);
        otp.authorize(RequestFingerprint.of(PURPOSE, depositId, quote.id()),
                userId, request.otpChallengeId(),
                request.otpCode(), PURPOSE, digest(quote));
        LocalDateTime current = now();
        int updated = jdbc.update("UPDATE deposit_contract SET status='CLOSURE_PENDING',"
                        + "payout_amount=?,closure_quote_id=?,closure_request_key=?"
                        + " WHERE deposit_id=? AND user_id=? AND status='ACTIVE'"
                        + " AND installments_paid=? AND maturity_at>?"
                        + " AND (next_due_at IS NULL OR next_due_at>?)",
                quote.payout(), quote.id(), request.idempotencyKey(), depositId, userId,
                quote.installmentsPaid(), Timestamp.valueOf(current), Timestamp.valueOf(current));
        if (updated != 1) {
            Contract latest = require(userId, depositId);
            if (("CLOSURE_PENDING".equals(latest.status()) || "CLOSED".equals(latest.status()))
                    && request.idempotencyKey().equals(latest.closureRequestKey())
                    && request.quoteId().equals(latest.closureQuoteId()))
                return settle(latest);
            throw new ConflictException("Deposit changed before closure. Refresh and get a new quote.");
        }
        return settle(require(userId, depositId));
    }

    public void recover() {
        jdbc.query("SELECT * FROM deposit_contract WHERE status='CLOSURE_PENDING'"
                        + " FETCH FIRST 50 ROWS ONLY", this::map)
                .forEach(contract -> {
                    try {
                        settle(contract);
                    } catch (Exception failure) {
                        org.slf4j.LoggerFactory.getLogger(getClass())
                                .warn("Deposit closure recovery deferred: {}", failure.toString());
                    }
                });
    }

    private DepositClosureResponse settle(Contract contract) {
        if ("CLOSED".equals(contract.status())) return response(contract);
        if (!"CLOSURE_PENDING".equals(contract.status()))
            throw new ConflictException("Deposit is not awaiting closure.");
        var receipt = ledger.post(new DepositLedgerCommand("deposit-close-" + contract.id(),
                contract.userId(), contract.accountId(), "PAYOUT", contract.payoutAmount(),
                contract.kind() + " early closure " + contract.id()));
        transactions.executeWithoutResult(tx -> {
            int changed = jdbc.update("UPDATE deposit_contract SET status='CLOSED',"
                            + "payout_reference=?,closed_at=? WHERE deposit_id=?"
                            + " AND status='CLOSURE_PENDING'",
                    receipt.reference(), Timestamp.valueOf(now()), contract.id());
            if (changed == 1)
                notifications.publish(contract.userId(), "DEPOSIT_CLOSED", "Deposit closed",
                        "Your " + contract.kind() + " was closed early. Payout reference: "
                                + receipt.reference());
        });
        return response(require(contract.userId(), contract.id()));
    }

    private void requireClosable(Contract contract) {
        LocalDateTime current = now();
        if (!"ACTIVE".equals(contract.status()) || contract.maturityAt() == null
                || !contract.maturityAt().isAfter(current))
            throw new ConflictException("Only an active deposit before maturity can be closed early.");
        if (contract.nextDueAt() != null && !contract.nextDueAt().isAfter(current))
            throw new ConflictException("Pay the due RD installment before requesting closure.");
    }

    private void requireActiveAccount(Contract contract) {
        var account = accounts.account(contract.userId(), contract.accountId());
        if (!"ACTIVE".equals(account.status()) || !"INR".equals(account.currencyCode()))
            throw new ConflictException("An active rupee account is required for the closure payout.");
    }

    private ClosureQuote requireQuote(Long userId, String depositId, String quoteId, Contract contract) {
        ClosureQuote quote = jdbc.query("SELECT * FROM deposit_closure_quote WHERE closure_quote_id=?"
                        + " AND deposit_id=? AND user_id=?", this::mapQuote,
                quoteId, depositId, userId).stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Deposit closure quote was not found."));
        if (!quote.expiresAt().isAfter(now()) || quote.installmentsPaid() != contract.installmentsPaid())
            throw new ConflictException("Deposit closure quote expired or changed. Get a new quote.");
        return quote;
    }

    private Contract require(Long userId, String depositId) {
        return jdbc.query("SELECT * FROM deposit_contract WHERE deposit_id=? AND user_id=?",
                this::map, depositId, userId).stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Deposit was not found."));
    }

    private List<Installment> installments(String depositId) {
        return jdbc.query("SELECT amount,paid_at FROM deposit_installment WHERE deposit_id=?"
                        + " ORDER BY installment_number",
                (rs, row) -> new Installment(rs.getBigDecimal(1), rs.getTimestamp(2).toLocalDateTime()),
                depositId);
    }

    private Contract map(ResultSet rs, int row) throws SQLException {
        return new Contract(rs.getString("deposit_id"), rs.getLong("user_id"),
                rs.getLong("source_account_id"), rs.getString("kind"), rs.getBigDecimal("annual_rate"),
                rs.getString("status"), rs.getInt("installments_paid"),
                local(rs, "maturity_at"), local(rs, "next_due_at"), rs.getBigDecimal("payout_amount"),
                rs.getString("payout_reference"), rs.getString("closure_quote_id"),
                rs.getString("closure_request_key"));
    }

    private ClosureQuote mapQuote(ResultSet rs, int row) throws SQLException {
        return new ClosureQuote(rs.getString("closure_quote_id"), rs.getString("deposit_id"),
                rs.getInt("installments_paid"), rs.getBigDecimal("payout_amount"),
                local(rs, "expires_at"));
    }

    private DepositClosureResponse response(Contract contract) {
        return new DepositClosureResponse(contract.id(), contract.status(),
                contract.payoutAmount(), contract.payoutReference());
    }

    private static String digest(ClosureQuote quote) {
        return RequestFingerprint.of(PURPOSE, quote.id(), quote.depositId(),
                quote.installmentsPaid(), quote.payout());
    }

    private static LocalDateTime local(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toLocalDateTime();
    }

    private static LocalDateTime now() { return LocalDateTime.now(ZoneOffset.UTC); }

    private record Installment(BigDecimal amount, LocalDateTime paidAt) {}
    private record ClosureQuote(String id, String depositId, int installmentsPaid,
            BigDecimal payout, LocalDateTime expiresAt) {}
    private record Contract(String id, Long userId, Long accountId, String kind,
            BigDecimal rate, String status, int installmentsPaid, LocalDateTime maturityAt,
            LocalDateTime nextDueAt, BigDecimal payoutAmount, String payoutReference,
            String closureQuoteId, String closureRequestKey) {}
}
