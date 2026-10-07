package com.netbanking.payment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netbanking.common.exception.ResourceNotFoundException;
import com.netbanking.contracts.LedgerCommand;
import com.netbanking.payment.api.PaymentHistoryResponse;
import com.netbanking.payment.api.PaymentSearchFilter;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class PaymentSearchService {
    private static final String SELECT = "SELECT * FROM payment_operation";

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public PaymentSearchService(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public Page<PaymentHistoryResponse> search(
            Long userId, PaymentSearchFilter filter, int page, int size) {
        validatePage(page, size);
        Query query = query(userId, filter);
        Long total =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM payment_operation" + query.where(),
                        Long.class,
                        query.arguments().toArray());

        List<Object> pageArguments = new ArrayList<>(query.arguments());
        pageArguments.add((long) page * size);
        pageArguments.add(size);
        List<PaymentHistoryResponse> content =
                jdbc.query(
                        SELECT
                                + query.where()
                                + " ORDER BY created_at DESC, payment_id DESC"
                                + " OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",
                        this::map,
                        pageArguments.toArray());
        return new PageImpl<>(named(content), PageRequest.of(page, size), total == null ? 0 : total);
    }

    public PaymentHistoryResponse get(Long userId, Long paymentId) {
        return named(
                        jdbc.query(
                                SELECT + " WHERE user_id = ? AND payment_id = ?",
                                this::map,
                                userId,
                                paymentId))
                .stream()
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Payment was not found."));
    }

    private Query query(Long userId, PaymentSearchFilter filter) {
        StringBuilder where = new StringBuilder(" WHERE user_id = ?");
        List<Object> arguments = new ArrayList<>();
        arguments.add(userId);
        if (filter.kind() != null) {
            where.append(" AND kind = ?");
            arguments.add(filter.kind().name());
        }
        if (filter.status() != null) {
            where.append(" AND state = ?");
            arguments.add(filter.status().name());
        }
        if (filter.from() != null) {
            where.append(" AND created_at >= ?");
            arguments.add(Timestamp.valueOf(filter.from().atStartOfDay()));
        }
        if (filter.to() != null) {
            where.append(" AND created_at < ?");
            arguments.add(Timestamp.valueOf(filter.to().plusDays(1).atStartOfDay()));
        }
        if (filter.transactionReference() != null) {
            where.append(" AND transaction_reference = ?");
            arguments.add(filter.transactionReference());
        }
        return new Query(where.toString(), List.copyOf(arguments));
    }

    private PaymentHistoryResponse map(ResultSet row, int index) throws SQLException {
        try {
            LedgerCommand command =
                    json.readValue(row.getString("ledger_command"), LedgerCommand.class);
            PaymentWorkflowStore.Details details =
                    json.readValue(
                            row.getString("payment_details"), PaymentWorkflowStore.Details.class);
            return new PaymentHistoryResponse(
                    row.getLong("payment_id"),
                    row.getString("kind"),
                    row.getString("state"),
                    row.getObject("transaction_id", Long.class),
                    row.getString("transaction_reference"),
                    command.amount(),
                    command.currencyCode(),
                    command.sourceAccountId(),
                    details.beneficiaryId(),
                    details.billerId(),
                    details.billReference(),
                    details.narration(),
                    row.getTimestamp("created_at").toInstant(),
                    null,
                    null);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored payment details are invalid.", exception);
        }
    }

    /** Adds the customer's current beneficiary nickname and the biller name to each row. */
    private List<PaymentHistoryResponse> named(List<PaymentHistoryResponse> rows) {
        Map<Long, String> nicknames =
                names(
                        "SELECT beneficiary_id, nickname FROM beneficiary WHERE beneficiary_id IN ",
                        rows.stream().map(PaymentHistoryResponse::beneficiaryId).toList());
        Map<Long, String> billers =
                names(
                        "SELECT biller_id, biller_name FROM biller WHERE biller_id IN ",
                        rows.stream().map(PaymentHistoryResponse::billerId).toList());
        if (nicknames.isEmpty() && billers.isEmpty()) return rows;
        return rows.stream()
                .map(
                        row ->
                                row.named(
                                        nicknames.get(row.beneficiaryId()),
                                        billers.get(row.billerId())))
                .toList();
    }

    private Map<Long, String> names(String select, List<Long> ids) {
        List<Long> distinct = ids.stream().filter(java.util.Objects::nonNull).distinct().toList();
        Map<Long, String> names = new HashMap<>();
        if (distinct.isEmpty()) return names;
        String placeholders = String.join(",", java.util.Collections.nCopies(distinct.size(), "?"));
        jdbc.query(
                select + "(" + placeholders + ")",
                (RowCallbackHandler) row -> names.put(row.getLong(1), row.getString(2)),
                distinct.toArray());
        return names;
    }

    private static void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException(
                    "Page must be non-negative and size must be between 1 and 100.");
        }
    }

    private record Query(String where, List<Object> arguments) {}
}
