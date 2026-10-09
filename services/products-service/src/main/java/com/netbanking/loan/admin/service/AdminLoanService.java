package com.netbanking.loan.admin.service;

import com.netbanking.loan.admin.api.AdminLoanCurrencySummary;
import com.netbanking.loan.admin.api.AdminLoanResponse;
import com.netbanking.loan.admin.api.AdminLoanSummaryResponse;
import com.netbanking.loan.domain.Loan;
import com.netbanking.loan.domain.LoanStatus;
import com.netbanking.loan.repository.LoanCurrencyTotals;
import com.netbanking.loan.repository.LoanRepository;

import jakarta.persistence.criteria.Predicate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Locale;

@Service
@Transactional(readOnly = true)
public class AdminLoanService {
    private final LoanRepository loans;

    public AdminLoanService(LoanRepository loans) {
        this.loans = loans;
    }

    public Page<AdminLoanResponse> search(
            String query, Long customerId, LoanStatus status, int page, int size) {
        validatePage(page, size);
        if (customerId != null && customerId <= 0) {
            throw new IllegalArgumentException("Customer identifier must be positive.");
        }
        String normalizedQuery = query == null || query.isBlank() ? null : query.strip();
        return loans.findAll(
                        specification(normalizedQuery, customerId, status),
                        PageRequest.of(
                                page,
                                size,
                                Sort.by(
                                        Sort.Order.desc("disbursedOn"),
                                        Sort.Order.desc("loanId"))))
                .map(AdminLoanService::toResponse);
    }

    public AdminLoanSummaryResponse summary() {
        var currencies =
                loans.summarizeAmountsByCurrency().stream()
                        .map(
                                total ->
                                        new AdminLoanCurrencySummary(
                                                total.getCurrencyCode().trim(),
                                                total.getPrincipalProvided(),
                                                total.getOutstandingPrincipal()))
                        .toList();
        return new AdminLoanSummaryResponse(
                loans.count(), loans.countByLoanStatus(LoanStatus.ACTIVE), currencies);
    }

    private static Specification<Loan> specification(
            String query, Long customerId, LoanStatus status) {
        return (root, ignored, builder) -> {
            Collection<Predicate> predicates = new ArrayList<>();
            if (query != null) {
                String pattern = "%" + escapeLike(query.toLowerCase(Locale.ROOT)) + "%";
                predicates.add(
                        builder.like(builder.lower(root.get("loanAccountNumber")), pattern, '\\'));
            }
            if (customerId != null) {
                predicates.add(builder.equal(root.get("customerId"), customerId));
            }
            if (status != null) {
                predicates.add(builder.equal(root.get("loanStatus"), status));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static AdminLoanResponse toResponse(Loan loan) {
        return new AdminLoanResponse(
                loan.getLoanId(),
                loan.getCustomerId(),
                loan.getLoanAccountNumber(),
                loan.getLoanType().name(),
                loan.getPrincipalAmount(),
                loan.getOutstandingPrincipal(),
                loan.getInterestRate(),
                loan.getTermMonths(),
                loan.getEmiAmount(),
                loan.getCurrencyCode().trim(),
                loan.getDisbursedOn(),
                loan.getNextDueDate(),
                loan.getMaturityDate(),
                loan.getLoanStatus().name());
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
