package com.netbanking.ledger.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netbanking.account.repository.BankAccountRepository;
import com.netbanking.account.service.AccountService;
import com.netbanking.audit.service.AuditLogService;
import com.netbanking.common.exception.ConflictException;
import com.netbanking.common.exception.ResourceNotFoundException;
import com.netbanking.contracts.DepositLedgerCommand;
import com.netbanking.contracts.LedgerReceipt;
import com.netbanking.contracts.RequestFingerprint;
import com.netbanking.transaction.domain.EntryType;
import com.netbanking.transaction.domain.TransactionStatus;
import com.netbanking.transaction.domain.TransactionType;
import com.netbanking.transaction.service.CreateTransactionCommand;
import com.netbanking.transaction.service.TransactionService;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DepositLedgerService {
    private static final String CALLER = "products-service:deposits";
    private final BankAccountRepository accounts;
    private final AccountService ownership;
    private final TransactionService transactions;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final AuditLogService audit;

    public DepositLedgerService(BankAccountRepository accounts, AccountService ownership,
            TransactionService transactions, JdbcTemplate jdbc, ObjectMapper json,
            AuditLogService audit) {
        this.accounts = accounts;
        this.ownership = ownership;
        this.transactions = transactions;
        this.jdbc = jdbc;
        this.json = json;
        this.audit = audit;
    }

    @Transactional
    public LedgerReceipt post(DepositLedgerCommand command) {
        if (!"FUND".equals(command.direction()) && !"PAYOUT".equals(command.direction()))
            throw new IllegalArgumentException("Deposit direction must be FUND or PAYOUT.");
        if (command.amount().scale() > 2)
            throw new IllegalArgumentException("Deposit amount must have at most two decimal places.");
        String fingerprint = RequestFingerprint.of(command.userId(), command.accountId(),
                command.direction(), command.amount(), command.narration());
        LedgerReceipt previous = prior(command.operationId(), fingerprint);
        if (previous != null) return previous;
        ownership.requireOwnership(command.userId(), command.accountId());
        var account = accounts.findByIdForUpdate(command.accountId())
                .orElseThrow(() -> new ResourceNotFoundException("Account was not found."));
        previous = prior(command.operationId(), fingerprint);
        if (previous != null) return previous;
        if (!"INR".equals(account.getCurrencyCode().trim()))
            throw new IllegalArgumentException("Deposits require an INR account.");
        jdbc.update("INSERT INTO ledger_operation (caller, operation_id, request_fingerprint) VALUES (?, ?, ?)",
                CALLER, command.operationId(), fingerprint);
        boolean funding = "FUND".equals(command.direction());
        var created = transactions.createTransaction(new CreateTransactionCommand(
                funding ? command.accountId() : null,
                funding ? null : command.accountId(), null, command.userId(),
                funding ? TransactionType.WITHDRAWAL : TransactionType.DEPOSIT,
                command.amount(), "INR", command.narration()));
        transactions.changeStatus(created.transactionId(), TransactionStatus.PROCESSING,
                command.userId(), null);
        if (funding) account.debit(command.amount());
        else account.credit(command.amount());
        transactions.postEntry(created.transactionId(), command.accountId(),
                funding ? EntryType.DEBIT : EntryType.CREDIT, account.getCurrentBalance());
        var completed = transactions.changeStatus(created.transactionId(), TransactionStatus.COMPLETED,
                command.userId(), null);
        var receipt = new LedgerReceipt(completed.transactionId(), completed.reference(),
                completed.status().name(), completed.amount(), completed.currencyCode());
        try {
            jdbc.update("UPDATE ledger_operation SET receipt = ? WHERE caller = ? AND operation_id = ?",
                    json.writeValueAsString(receipt), CALLER, command.operationId());
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        audit.record(command.userId(), funding ? "DEPOSIT_FUNDED" : "DEPOSIT_PAID_OUT",
                "TRANSACTION", receipt.reference(), "SUCCESS");
        return receipt;
    }

    private LedgerReceipt prior(String operationId, String fingerprint) {
        var rows = jdbc.query("SELECT request_fingerprint, receipt FROM ledger_operation WHERE caller = ? AND operation_id = ?",
                (rs, n) -> new String[] {rs.getString(1), rs.getString(2)}, CALLER, operationId);
        if (rows.isEmpty()) return null;
        if (!fingerprint.equals(rows.get(0)[0]))
            throw new ConflictException("Operation key was already used for different deposit instructions.");
        try {
            return json.readValue(rows.get(0)[1], LedgerReceipt.class);
        } catch (Exception e) {
            throw new IllegalStateException("Stored deposit ledger receipt is invalid.", e);
        }
    }
}
