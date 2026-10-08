package com.netbanking.statement.service;

import com.netbanking.account.api.AccountSummaryResponse;
import com.netbanking.account.service.AccountService;
import com.netbanking.statement.api.StatementFilter;
import com.netbanking.statement.api.StatementTransactionStatus;
import com.netbanking.statement.api.StatementTransactionType;
import com.netbanking.transaction.api.TransactionResponse;
import com.netbanking.transaction.domain.AccountTransactionEntry;
import com.netbanking.transaction.domain.TransactionStatus;
import com.netbanking.transaction.domain.TransactionType;
import com.netbanking.transaction.repository.AccountTransactionEntryRepository;
import com.netbanking.transaction.service.TransactionService;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class StatementService {

    private static final int EXPORT_PAGE_SIZE = 500;
    private static final int MAX_EXPORT_ROWS = 10_000;
    private static final int MAX_EMAIL_BYTES = 5 * 1024 * 1024;
    private static final Sort NEWEST_FIRST =
            Sort.by(Sort.Order.desc("postedAt"), Sort.Order.desc("entryId"));
    private static final String CSV_HEADER =
            "entry_id,transaction_id,reference,type,status,entry_type,amount,currency_code,balance_after,narration,posted_at\r\n";

    private final AccountService accountService;
    private final AccountTransactionEntryRepository entryRepository;
    private final StatementPdfRenderer pdfRenderer;
    private final StatementMailClient mail;

    public StatementService(
            AccountService accountService,
            AccountTransactionEntryRepository entryRepository,
            StatementPdfRenderer pdfRenderer,
            StatementMailClient mail) {
        this.accountService = accountService;
        this.entryRepository = entryRepository;
        this.pdfRenderer = pdfRenderer;
        this.mail = mail;
    }

    public Page<TransactionResponse> getStatement(
            Long userId, Long accountId, StatementFilter filter, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException(
                    "Page must be non-negative and size must be between 1 and 100.");
        }
        accountService.requireOwnership(userId, accountId);
        return entryRepository
                .findAll(specification(accountId, filter), PageRequest.of(page, size, NEWEST_FIRST))
                .map(TransactionService::toResponse);
    }

    public byte[] exportCsv(Long userId, Long accountId, StatementFilter filter) {
        accountService.requireOwnership(userId, accountId);
        StringBuilder csv = new StringBuilder(CSV_HEADER);
        for (TransactionResponse row : collect(accountId, filter)) {
            appendCsvRow(csv, row);
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    public StatementDocument exportPdf(Long userId, Long accountId, StatementFilter filter) {
        AccountSummaryResponse account = accountService.getOwnedAccount(userId, accountId);
        List<TransactionResponse> rows = new ArrayList<>(collect(accountId, filter));
        Collections.reverse(rows);
        LocalDateTime generatedAt = LocalDateTime.now();
        byte[] pdf =
                pdfRenderer.render(
                        new StatementPdfRenderer.Account(
                                account.accountType(),
                                account.accountNumber(),
                                account.currencyCode(),
                                account.nickname()),
                        new StatementPdfRenderer.Period(
                                filter.from(),
                                filter.to(),
                                filter.type() == null ? null : filter.type().name(),
                                filter.status() == null ? null : filter.status().name()),
                        rows,
                        generatedAt);
        String last4 = StatementPdfRenderer.masked(account.accountNumber()).substring(5);
        return new StatementDocument(
                "statement-" + last4 + "-" + generatedAt.toLocalDate() + ".pdf",
                pdf,
                last4,
                rows.size());
    }

    /** Emails the same document {@link #exportPdf} would download to the customer's registered address. */
    public StatementMailClient.Receipt emailPdf(Long userId, Long accountId, StatementFilter filter) {
        StatementDocument document = exportPdf(userId, accountId, filter);
        if (document.content().length > MAX_EMAIL_BYTES) {
            throw new ResponseStatusException(
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    "This statement is too large to email; narrow the date range.");
        }
        String period = periodText(filter);
        return mail.send(
                userId,
                "Your account statement (account ending " + document.accountLast4() + ")",
                "Your statement for the account ending "
                        + document.accountLast4()
                        + " ("
                        + period
                        + ", "
                        + document.entries()
                        + (document.entries() == 1 ? " entry" : " entries")
                        + ") is attached as a PDF.\n\n"
                        + "If you did not request this statement, sign in and review your recent "
                        + "activity, or contact the bank.",
                document.filename(),
                document.content());
    }

    private static String periodText(StatementFilter filter) {
        if (filter.from() == null && filter.to() == null) return "all available history";
        if (filter.from() == null) return "up to " + filter.to();
        if (filter.to() == null) return "from " + filter.from();
        return filter.from() + " to " + filter.to();
    }

    public record StatementDocument(
            String filename, byte[] content, String accountLast4, int entries) {}

    private List<TransactionResponse> collect(Long accountId, StatementFilter filter) {
        Specification<AccountTransactionEntry> specification = specification(accountId, filter);
        List<TransactionResponse> rows = new ArrayList<>();
        int pageNumber = 0;
        Page<AccountTransactionEntry> result;
        do {
            result =
                    entryRepository.findAll(
                            specification,
                            PageRequest.of(pageNumber++, EXPORT_PAGE_SIZE, NEWEST_FIRST));
            if (result.getTotalElements() > MAX_EXPORT_ROWS) {
                throw new ResponseStatusException(
                        HttpStatus.PAYLOAD_TOO_LARGE,
                        "Statement has more than 10000 rows; narrow the date range.");
            }
            for (AccountTransactionEntry entry : result.getContent()) {
                rows.add(TransactionService.toResponse(entry));
            }
        } while (result.hasNext());
        return rows;
    }

    private static Specification<AccountTransactionEntry> specification(
            Long accountId, StatementFilter filter) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("accountId"), accountId));
            if (filter.from() != null) {
                predicates.add(
                        builder.greaterThanOrEqualTo(
                                root.get("postedAt"), filter.from().atStartOfDay()));
            }
            if (filter.to() != null) {
                LocalDateTime before = filter.to().plusDays(1).atStartOfDay();
                predicates.add(builder.lessThan(root.get("postedAt"), before));
            }
            if (filter.type() != null || filter.status() != null) {
                Join<Object, Object> transaction = root.join("transaction");
                if (filter.type() != null) {
                    predicates.add(
                            builder.equal(
                                    transaction.get("transactionType"),
                                    toDomainType(filter.type())));
                }
                if (filter.status() != null) {
                    predicates.add(
                            builder.equal(
                                    transaction.get("transactionStatus"),
                                    toDomainStatus(filter.status())));
                }
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static TransactionType toDomainType(StatementTransactionType type) {
        return switch (type) {
            case TRANSFER -> TransactionType.TRANSFER;
            case DEPOSIT -> TransactionType.DEPOSIT;
            case WITHDRAWAL -> TransactionType.WITHDRAWAL;
            case LOAN_PAYMENT -> TransactionType.LOAN_PAYMENT;
            case REVERSAL -> TransactionType.REVERSAL;
        };
    }

    private static TransactionStatus toDomainStatus(StatementTransactionStatus status) {
        return switch (status) {
            case PENDING -> TransactionStatus.PENDING;
            case PROCESSING -> TransactionStatus.PROCESSING;
            case COMPLETED -> TransactionStatus.COMPLETED;
            case FAILED -> TransactionStatus.FAILED;
            case REVERSED -> TransactionStatus.REVERSED;
        };
    }

    private static void appendCsvRow(StringBuilder csv, TransactionResponse row) {
        csv.append(row.entryId())
                .append(',')
                .append(row.transactionId())
                .append(',')
                .append(csvCell(row.reference()))
                .append(',')
                .append(csvCell(row.type()))
                .append(',')
                .append(csvCell(row.status()))
                .append(',')
                .append(csvCell(row.entryType()))
                .append(',')
                .append(row.amount().toPlainString())
                .append(',')
                .append(csvCell(row.currencyCode()))
                .append(',')
                .append(row.balanceAfter().toPlainString())
                .append(',')
                .append(csvCell(row.narration()))
                .append(',')
                .append(csvCell(row.postedAt().toString()))
                .append("\r\n");
    }

    private static String csvCell(String value) {
        String safe = value == null ? "" : value;
        String trimmed = safe.stripLeading();
        if (!trimmed.isEmpty() && "=+-@".indexOf(trimmed.charAt(0)) >= 0) {
            safe = "'" + safe;
        }
        return '"' + safe.replace("\"", "\"\"") + '"';
    }
}
