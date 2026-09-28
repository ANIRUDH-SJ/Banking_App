package com.netbanking.admin.service;

import com.netbanking.admin.api.AdminTransactionResponse;
import com.netbanking.admin.api.AdminTransactionSearchFilter;
import com.netbanking.transaction.domain.BankTransaction;
import com.netbanking.transaction.repository.BankTransactionRepository;

import jakarta.persistence.criteria.Predicate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Locale;

@Service
@Transactional(readOnly = true)
public class AdminTransactionService {
    private final BankTransactionRepository transactions;

    public AdminTransactionService(BankTransactionRepository transactions) {
        this.transactions = transactions;
    }

    public Page<AdminTransactionResponse> search(
            AdminTransactionSearchFilter filter, int page, int size) {
        validatePage(page, size);
        return transactions
                .findAll(
                        specification(filter),
                        PageRequest.of(
                                page,
                                size,
                                Sort.by(
                                        Sort.Order.desc("initiatedAt"),
                                        Sort.Order.desc("transactionId"))))
                .map(AdminTransactionService::toResponse);
    }

    private static Specification<BankTransaction> specification(
            AdminTransactionSearchFilter filter) {
        return (root, ignored, builder) -> {
            Collection<Predicate> predicates = new ArrayList<>();
            if (filter.reference() != null) {
                String pattern =
                        "%"
                                + escapeLike(filter.reference().toLowerCase(Locale.ROOT))
                                + "%";
                predicates.add(
                        builder.like(
                                builder.lower(root.get("transactionReference")), pattern, '\\'));
            }
            if (filter.accountId() != null) {
                predicates.add(
                        builder.or(
                                builder.equal(root.get("debitAccountId"), filter.accountId()),
                                builder.equal(root.get("creditAccountId"), filter.accountId())));
            }
            if (filter.initiatedByUserId() != null) {
                predicates.add(
                        builder.equal(
                                root.get("initiatedByUserId"), filter.initiatedByUserId()));
            }
            if (filter.type() != null) {
                predicates.add(builder.equal(root.get("transactionType"), filter.type()));
            }
            if (filter.status() != null) {
                predicates.add(builder.equal(root.get("transactionStatus"), filter.status()));
            }
            if (filter.from() != null) {
                predicates.add(
                        builder.greaterThanOrEqualTo(
                                root.get("initiatedAt"), filter.from().atStartOfDay()));
            }
            if (filter.to() != null) {
                LocalDateTime before = filter.to().plusDays(1).atStartOfDay();
                predicates.add(builder.lessThan(root.get("initiatedAt"), before));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static AdminTransactionResponse toResponse(BankTransaction transaction) {
        return new AdminTransactionResponse(
                transaction.getTransactionId(),
                transaction.getTransactionReference(),
                transaction.getDebitAccountId(),
                transaction.getCreditAccountId(),
                transaction.getBeneficiaryId(),
                transaction.getInitiatedByUserId(),
                transaction.getTransactionType().name(),
                transaction.getTransactionStatus().name(),
                transaction.getAmount(),
                transaction.getCurrencyCode().trim(),
                transaction.getNarration(),
                transaction.getInitiatedAt(),
                transaction.getCompletedAt(),
                transaction.getFailureReason());
    }

    private static void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException(
                    "Page must be non-negative and size must be between 1 and 100.");
        }
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
