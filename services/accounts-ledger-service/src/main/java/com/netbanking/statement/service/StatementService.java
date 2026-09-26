package com.netbanking.statement.service;

import com.netbanking.account.service.AccountService;
import com.netbanking.statement.api.StatementFilter;
import com.netbanking.transaction.api.TransactionResponse;
import com.netbanking.transaction.domain.AccountTransactionEntry;
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
import java.util.List;

@Service
@Transactional(readOnly = true)
public class StatementService {

    private static final int EXPORT_PAGE_SIZE = 500;
    private static final int MAX_EXPORT_ROWS = 10_000;
    private static final Sort NEWEST_FIRST =
            Sort.by(Sort.Order.desc("postedAt"), Sort.Order.desc("entryId"));
    private static final String CSV_HEADER =
            "entry_id,transaction_id,reference,type,status,entry_type,amount,currency_code,balance_after,narration,posted_at\r\n";

    private final AccountService accountService;
    private final AccountTransactionEntryRepository entryRepository;

    public StatementService(
            AccountService accountService, AccountTransactionEntryRepository entryRepository) {
        this.accountService = accountService;
        this.entryRepository = entryRepository;
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
        Specification<AccountTransactionEntry> specification = specification(accountId, filter);
        StringBuilder csv = new StringBuilder(CSV_HEADER);
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
                appendCsvRow(csv, TransactionService.toResponse(entry));
            }
        } while (result.hasNext());
        return csv.toString().getBytes(StandardCharsets.UTF_8);
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
                            builder.equal(transaction.get("transactionType"), filter.type()));
                }
                if (filter.status() != null) {
                    predicates.add(
                            builder.equal(transaction.get("transactionStatus"), filter.status()));
                }
            }
            return builder.and(predicates.toArray(Predicate[]::new));
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
