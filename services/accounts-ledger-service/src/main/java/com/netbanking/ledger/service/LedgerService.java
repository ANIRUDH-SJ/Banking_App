package com.netbanking.ledger.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netbanking.account.domain.BankAccount;
import com.netbanking.account.repository.BankAccountRepository;
import com.netbanking.account.service.AccountService;
import com.netbanking.audit.service.AuditLogService;
import com.netbanking.branch.repository.BranchRepository;
import com.netbanking.common.exception.*;
import com.netbanking.contracts.*;
import com.netbanking.transaction.domain.*;
import com.netbanking.transaction.service.*;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class LedgerService {
    private final BankAccountRepository accounts;
    private final BranchRepository branches;
    private final AccountService ownership;
    private final TransactionService transactions;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final AuditLogService audit;

    public LedgerService(
            BankAccountRepository accounts,
            BranchRepository branches,
            AccountService ownership,
            TransactionService transactions,
            JdbcTemplate jdbc,
            ObjectMapper json,
            AuditLogService audit) {
        this.accounts = accounts;
        this.branches = branches;
        this.ownership = ownership;
        this.transactions = transactions;
        this.jdbc = jdbc;
        this.json = json;
        this.audit = audit;
    }

    @Transactional
    public LedgerReceipt post(String caller, LedgerCommand command) {
        if (("payments-service".equals(caller)
                        && !List.of("TRANSFER", "WITHDRAWAL").contains(command.type()))
                || ("products-service".equals(caller) && !"LOAN_PAYMENT".equals(command.type())))
            throw new SecurityException("Caller cannot post this transaction type.");
        String fingerprint =
                RequestFingerprint.of(
                        command.userId(),
                        command.sourceAccountId(),
                        command.destinationAccountNumber(),
                        command.destinationIfsc(),
                        command.type(),
                        command.amount(),
                        command.currencyCode(),
                        command.narration());
        LedgerReceipt prior = prior(caller, command.operationId(), fingerprint);
        if (prior != null) return prior;
        ownership.requireOwnership(command.userId(), command.sourceAccountId());
        Long destinationId = null;
        if ("TRANSFER".equals(command.type())) {
            destinationId =
                    accounts.findAccountIdByAccountNumber(command.destinationAccountNumber())
                            .orElseThrow(
                                    () ->
                                            new IllegalArgumentException(
                                                    "Only transfers to accounts in this bank are"
                                                            + " supported."));
            if (destinationId.equals(command.sourceAccountId()))
                throw new IllegalArgumentException("Source and destination must differ.");
        }
        // Stable lock order prevents opposite-direction transfers from deadlocking.
        BankAccount source, destination = null;
        if (destinationId != null && destinationId < command.sourceAccountId()) {
            destination = lock(destinationId);
            source = lock(command.sourceAccountId());
        } else {
            source = lock(command.sourceAccountId());
            if (destinationId != null) destination = lock(destinationId);
        }
        prior = prior(caller, command.operationId(), fingerprint);
        if (prior != null) return prior;
        if (destination != null) {
            var branch =
                    branches.findById(destination.getBranchId())
                            .orElseThrow(
                                    () ->
                                            new ResourceNotFoundException(
                                                    "Destination branch was not found."));
            if (!branch.getIfscCode().equalsIgnoreCase(command.destinationIfsc()))
                throw new IllegalArgumentException("Destination IFSC does not match the account.");
        }
        if (!source.getCurrencyCode().trim().equals(command.currencyCode())
                || (destination != null
                        && !destination.getCurrencyCode().trim().equals(command.currencyCode())))
            throw new IllegalArgumentException("Account currencies must match.");
        jdbc.update(
                "INSERT INTO ledger_operation (caller, operation_id, request_fingerprint) VALUES"
                        + " (?, ?, ?)",
                caller,
                command.operationId(),
                fingerprint);
        var transaction =
                transactions.createTransaction(
                        new CreateTransactionCommand(
                                source.getAccountId(),
                                destinationId,
                                null,
                                command.userId(),
                                TransactionType.valueOf(command.type()),
                                command.amount(),
                                command.currencyCode(),
                                command.narration()));
        transactions.changeStatus(
                transaction.transactionId(), TransactionStatus.PROCESSING, command.userId(), null);
        source.debit(command.amount());
        transactions.postEntry(
                transaction.transactionId(),
                source.getAccountId(),
                EntryType.DEBIT,
                source.getCurrentBalance());
        if (destination != null) {
            destination.credit(command.amount());
            transactions.postEntry(
                    transaction.transactionId(),
                    destination.getAccountId(),
                    EntryType.CREDIT,
                    destination.getCurrentBalance());
        }
        var completed =
                transactions.changeStatus(
                        transaction.transactionId(),
                        TransactionStatus.COMPLETED,
                        command.userId(),
                        null);
        var receipt =
                new LedgerReceipt(
                        completed.transactionId(),
                        completed.reference(),
                        completed.status().name(),
                        completed.amount(),
                        completed.currencyCode());
        try {
            jdbc.update(
                    "UPDATE ledger_operation SET receipt = ? WHERE caller = ? AND operation_id = ?",
                    json.writeValueAsString(receipt),
                    caller,
                    command.operationId());
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        audit.record(
                command.userId(), "LEDGER_POSTED", "TRANSACTION", receipt.reference(), "SUCCESS");
        return receipt;
    }

    private BankAccount lock(Long id) {
        return accounts.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Account was not found."));
    }

    private LedgerReceipt prior(String caller, String operation, String fingerprint) {
        var rows =
                jdbc.query(
                        "SELECT request_fingerprint, receipt FROM ledger_operation WHERE caller = ?"
                                + " AND operation_id = ?",
                        (rs, n) -> new String[] {rs.getString(1), rs.getString(2)},
                        caller,
                        operation);
        if (rows.isEmpty()) return null;
        if (!fingerprint.equals(rows.get(0)[0]))
            throw new ConflictException(
                    "Operation key was already used for different ledger instructions.");
        try {
            return json.readValue(rows.get(0)[1], LedgerReceipt.class);
        } catch (Exception e) {
            throw new IllegalStateException("Stored ledger receipt is invalid.", e);
        }
    }
}
