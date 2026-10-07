package com.netbanking.deposit.service;

import com.netbanking.common.exception.ConflictException;
import com.netbanking.common.exception.ResourceNotFoundException;
import com.netbanking.contracts.DepositLedgerCommand;
import com.netbanking.discovery.DepositLedgerClient;
import com.netbanking.discovery.LedgerClient;
import com.netbanking.deposit.api.*;
import com.netbanking.events.NotificationPublisher;

import org.springframework.dao.DataIntegrityViolationException;
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
import java.util.Set;
import java.util.UUID;

@Service
public class DepositService {
    private static final Set<Integer> TERMS = Set.of(6, 12, 24, 36);
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final LedgerClient accounts;
    private final DepositLedgerClient ledger;
    private final DepositPricing pricing;
    private final NotificationPublisher notifications;

    public DepositService(JdbcTemplate jdbc, TransactionTemplate transactions,
            LedgerClient accounts, DepositLedgerClient ledger, DepositPricing pricing,
            NotificationPublisher notifications) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.accounts = accounts;
        this.ledger = ledger;
        this.pricing = pricing;
        this.notifications = notifications;
    }

    public DepositQuoteResponse quote(Long userId, DepositQuoteRequest request) {
        if (!"FD".equals(request.kind()) && !"RD".equals(request.kind()))
            throw new IllegalArgumentException("Deposit kind must be FD or RD.");
        if (!TERMS.contains(request.termMonths()))
            throw new IllegalArgumentException("Deposit term must be 6, 12, 24 or 36 months.");
        BigDecimal amount = request.amount();
        if (amount.scale() > 2 || amount.compareTo(new BigDecimal("10000000")) > 0
                || amount.compareTo("FD".equals(request.kind())
                        ? new BigDecimal("1000") : new BigDecimal("100")) < 0)
            throw new IllegalArgumentException("Deposit amount is outside the permitted range.");
        var source = accounts.account(userId, request.sourceAccountId());
        if (!"ACTIVE".equals(source.status()) || !"INR".equals(source.currencyCode()))
            throw new IllegalArgumentException("An active INR account is required.");
        BigDecimal rate = pricing.rate(request.kind());
        LocalDateTime now = now();
        BigDecimal estimate = pricing.estimate(request.kind(), amount, request.termMonths(),
                rate, now.toLocalDate());
        String quoteId = UUID.randomUUID().toString();
        LocalDateTime expiry = now.plusMinutes(10);
        jdbc.update("INSERT INTO deposit_quote (quote_id,user_id,source_account_id,kind,amount,term_months,annual_rate,estimated_maturity,expires_at) VALUES (?,?,?,?,?,?,?,?,?)",
                quoteId, userId, request.sourceAccountId(), request.kind(), amount,
                request.termMonths(), rate, estimate, Timestamp.valueOf(expiry));
        return new DepositQuoteResponse(quoteId, request.kind(), amount,
                request.termMonths(), rate, estimate, expiry.atOffset(ZoneOffset.UTC));
    }

    public DepositResponse open(Long userId, OpenDepositRequest request) {
        if (request.idempotencyKey().length() > 100 || request.quoteId().length() > 36)
            throw new IllegalArgumentException("Quote or idempotency key is too long.");
        Contract prior = byRequest(userId, request.idempotencyKey());
        if (prior != null) {
            if (!prior.quoteId().equals(request.quoteId()))
                throw new ConflictException("Idempotency key was used with another quote.");
            return settleFunding(prior);
        }
        var quotes = jdbc.query("SELECT * FROM deposit_quote WHERE quote_id = ? AND user_id = ?",
                (rs, n) -> new Quote(rs.getString("quote_id"), rs.getLong("source_account_id"),
                        rs.getString("kind"), rs.getBigDecimal("amount"), rs.getInt("term_months"),
                        rs.getBigDecimal("annual_rate"), rs.getBigDecimal("estimated_maturity"),
                        local(rs, "expires_at")), request.quoteId(), userId);
        if (quotes.isEmpty()) throw new ResourceNotFoundException("Deposit quote was not found.");
        Quote quote = quotes.get(0);
        if (!quote.expiresAt().isAfter(now()))
            throw new ConflictException("Deposit quote has expired. Request a new quote.");
        String id = UUID.randomUUID().toString();
        try {
            jdbc.update("INSERT INTO deposit_contract (deposit_id,quote_id,user_id,source_account_id,idempotency_key,kind,amount,term_months,annual_rate,estimated_maturity,status) VALUES (?,?,?,?,?,?,?,?,?,?,'PENDING')",
                    id, quote.id(), userId, quote.accountId(), request.idempotencyKey(),
                    quote.kind(), quote.amount(), quote.months(), quote.rate(), quote.estimate());
        } catch (DataIntegrityViolationException duplicate) {
            Contract existing = byRequest(userId, request.idempotencyKey());
            if (existing != null && existing.quoteId().equals(request.quoteId()))
                return settleFunding(existing);
            throw new ConflictException("Quote or idempotency key has already been used.");
        }
        return settleFunding(require(userId, id));
    }

    public List<DepositResponse> list(Long userId, int page, int size) {
        if (page < 0 || size < 1 || size > 100)
            throw new IllegalArgumentException("Invalid deposit page or size.");
        return jdbc.query("SELECT * FROM deposit_contract WHERE user_id = ? ORDER BY created_at DESC, deposit_id DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",
                this::map, userId, (long) page * size, size).stream().map(this::response).toList();
    }

    public DepositResponse get(Long userId, String id) {
        return response(require(userId, id));
    }

    public DepositResponse payDueInstallment(Long userId, String id) {
        Contract contract = require(userId, id);
        if (!"RD".equals(contract.kind()) || !"ACTIVE".equals(contract.status()))
            throw new ConflictException("Only an active RD can accept an installment.");
        if (contract.nextDueAt() == null || contract.nextDueAt().isAfter(now()))
            throw new ConflictException("No recurring installment is due.");
        settleInstallment(contract);
        return get(userId, id);
    }

    public void recover() {
        var pending = jdbc.query("SELECT * FROM deposit_contract WHERE status = 'PENDING' FETCH FIRST 50 ROWS ONLY",
                this::map);
        for (Contract contract : pending) attempt(() -> settleFunding(contract));
        var due = jdbc.query("SELECT * FROM deposit_contract WHERE status = 'ACTIVE' AND kind = 'RD' AND next_due_at <= ? FETCH FIRST 50 ROWS ONLY",
                this::map, Timestamp.valueOf(now()));
        for (Contract contract : due) attempt(() -> settleInstallment(contract));
        var mature = jdbc.query("SELECT * FROM deposit_contract WHERE status = 'ACTIVE' AND maturity_at <= ? AND (kind = 'FD' OR installments_paid = term_months) FETCH FIRST 50 ROWS ONLY",
                this::map, Timestamp.valueOf(now()));
        for (Contract contract : mature) attempt(() -> settleMaturity(contract));
        var payouts = jdbc.query("SELECT * FROM deposit_contract WHERE status = 'PAYOUT_PENDING' FETCH FIRST 50 ROWS ONLY",
                this::map);
        for (Contract contract : payouts) attempt(() -> settleMaturity(contract));
    }

    private void attempt(Runnable action) {
        try { action.run(); }
        catch (Exception failure) {
            org.slf4j.LoggerFactory.getLogger(getClass()).warn("Deposit recovery retry deferred: {}", failure.toString());
        }
    }

    private DepositResponse settleFunding(Contract contract) {
        if (!"PENDING".equals(contract.status())) return response(contract);
        var receipt = ledger.post(new DepositLedgerCommand("deposit-open-" + contract.id(),
                contract.userId(), contract.accountId(), "FUND", contract.amount(),
                contract.kind() + " opening " + contract.id()));
        LocalDateTime opened = now();
        transactions.executeWithoutResult(tx -> {
            int changed = jdbc.update("UPDATE deposit_contract SET status = 'ACTIVE', opened_at = ?, maturity_at = ?, installments_paid = 1, next_due_at = ? WHERE deposit_id = ? AND status = 'PENDING'",
                    Timestamp.valueOf(opened), Timestamp.valueOf(opened.plusMonths(contract.months())),
                    "RD".equals(contract.kind()) ? Timestamp.valueOf(opened.plusMonths(1)) : null,
                    contract.id());
            if (changed == 1) {
                jdbc.update("INSERT INTO deposit_installment (deposit_id,installment_number,amount,due_at,paid_at,ledger_reference) VALUES (?,?,?,?,?,?)",
                        contract.id(), 1, contract.amount(), Timestamp.valueOf(opened),
                        Timestamp.valueOf(opened), receipt.reference());
                notifications.publish(contract.userId(), "DEPOSIT_OPENED", "Deposit opened",
                        "Your " + contract.kind() + " is active. Reference: " + receipt.reference());
            }
        });
        return get(contract.userId(), contract.id());
    }

    private void settleInstallment(Contract contract) {
        if (!"ACTIVE".equals(contract.status()) || !"RD".equals(contract.kind())
                || contract.installmentsPaid() >= contract.months()
                || contract.nextDueAt() == null || contract.nextDueAt().isAfter(now())) return;
        int number = contract.installmentsPaid() + 1;
        var receipt = ledger.post(new DepositLedgerCommand("deposit-rd-" + contract.id() + "-" + number,
                contract.userId(), contract.accountId(), "FUND", contract.amount(),
                "RD installment " + number + " for " + contract.id()));
        LocalDateTime paid = now();
        transactions.executeWithoutResult(tx -> {
            int changed = jdbc.update("UPDATE deposit_contract SET installments_paid = ?, next_due_at = ? WHERE deposit_id = ? AND status = 'ACTIVE' AND installments_paid = ?",
                    number, number < contract.months()
                            ? Timestamp.valueOf(contract.openedAt().plusMonths(number)) : null,
                    contract.id(), contract.installmentsPaid());
            if (changed == 1) {
                jdbc.update("INSERT INTO deposit_installment (deposit_id,installment_number,amount,due_at,paid_at,ledger_reference) VALUES (?,?,?,?,?,?)",
                        contract.id(), number, contract.amount(), Timestamp.valueOf(contract.nextDueAt()),
                        Timestamp.valueOf(paid), receipt.reference());
                notifications.publish(contract.userId(), "DEPOSIT_INSTALLMENT", "RD installment collected",
                        "Installment " + number + " was collected. Reference: " + receipt.reference());
            }
        });
    }

    private void settleMaturity(Contract contract) {
        if ("MATURED".equals(contract.status())) return;
        if ("ACTIVE".equals(contract.status())) {
            BigDecimal payout = actualPayout(contract);
            jdbc.update("UPDATE deposit_contract SET status = 'PAYOUT_PENDING', payout_amount = ? WHERE deposit_id = ? AND status = 'ACTIVE' AND maturity_at <= ? AND (kind = 'FD' OR installments_paid = term_months)",
                    payout, contract.id(), Timestamp.valueOf(now()));
            contract = require(contract.userId(), contract.id());
        }
        if (!"PAYOUT_PENDING".equals(contract.status())) return;
        var receipt = ledger.post(new DepositLedgerCommand("deposit-maturity-" + contract.id(),
                contract.userId(), contract.accountId(), "PAYOUT", contract.payoutAmount(),
                contract.kind() + " maturity " + contract.id()));
        Contract completed = contract;
        transactions.executeWithoutResult(tx -> {
            int changed = jdbc.update("UPDATE deposit_contract SET status = 'MATURED', payout_reference = ? WHERE deposit_id = ? AND status = 'PAYOUT_PENDING'",
                    receipt.reference(), completed.id());
            if (changed == 1) notifications.publish(completed.userId(), "DEPOSIT_MATURITY",
                    "Deposit matured", "Your " + completed.kind() + " payout was credited. Reference: " + receipt.reference());
        });
    }

    private BigDecimal actualPayout(Contract contract) {
        var paid = jdbc.query("SELECT amount, paid_at FROM deposit_installment WHERE deposit_id = ? ORDER BY installment_number",
                (rs, n) -> new Paid(rs.getBigDecimal(1), rs.getTimestamp(2).toLocalDateTime()),
                contract.id());
        BigDecimal total = BigDecimal.ZERO;
        for (Paid installment : paid)
            total = total.add(installment.amount()).add(pricing.accrued(installment.amount(),
                    contract.rate(), installment.at().toLocalDate(), contract.maturityAt().toLocalDate()));
        return total.setScale(2, RoundingMode.HALF_EVEN);
    }

    private Contract byRequest(Long userId, String key) {
        return jdbc.query("SELECT * FROM deposit_contract WHERE user_id = ? AND idempotency_key = ?",
                this::map, userId, key).stream().findFirst().orElse(null);
    }

    private Contract require(Long userId, String id) {
        return jdbc.query("SELECT * FROM deposit_contract WHERE user_id = ? AND deposit_id = ?",
                this::map, userId, id).stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Deposit was not found."));
    }

    private DepositResponse response(Contract c) {
        return new DepositResponse(c.id(), c.kind(), c.status(), c.accountId(), c.amount(),
                c.months(), c.rate(), c.amount().multiply(BigDecimal.valueOf(c.installmentsPaid())),
                c.estimate(), c.payoutAmount(), c.payoutReference(), c.installmentsPaid(),
                c.openedAt(), c.maturityAt(), c.nextDueAt());
    }

    private Contract map(ResultSet rs, int n) throws SQLException {
        return new Contract(rs.getString("deposit_id"), rs.getString("quote_id"),
                rs.getLong("user_id"), rs.getLong("source_account_id"), rs.getString("kind"),
                rs.getBigDecimal("amount"), rs.getInt("term_months"), rs.getBigDecimal("annual_rate"),
                rs.getBigDecimal("estimated_maturity"), rs.getString("status"),
                rs.getInt("installments_paid"), local(rs, "opened_at"), local(rs, "maturity_at"),
                local(rs, "next_due_at"), rs.getBigDecimal("payout_amount"),
                rs.getString("payout_reference"));
    }

    private static LocalDateTime local(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toLocalDateTime();
    }

    private static LocalDateTime now() { return LocalDateTime.now(ZoneOffset.UTC); }

    private record Quote(String id, Long accountId, String kind, BigDecimal amount,
            int months, BigDecimal rate, BigDecimal estimate, LocalDateTime expiresAt) {}
    private record Paid(BigDecimal amount, LocalDateTime at) {}
    private record Contract(String id, String quoteId, Long userId, Long accountId,
            String kind, BigDecimal amount, int months, BigDecimal rate, BigDecimal estimate,
            String status, int installmentsPaid, LocalDateTime openedAt,
            LocalDateTime maturityAt, LocalDateTime nextDueAt, BigDecimal payoutAmount,
            String payoutReference) {}
}
