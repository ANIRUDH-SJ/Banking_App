package com.netbanking.account.admin.service;

import com.netbanking.account.admin.api.AdminAccountResponse;
import com.netbanking.account.api.AccountStatus;
import com.netbanking.account.domain.AccountHolder;
import com.netbanking.account.domain.BankAccount;
import com.netbanking.account.repository.AccountHolderRepository;
import com.netbanking.account.repository.BankAccountRepository;
import com.netbanking.audit.service.AuditLogService;
import com.netbanking.common.exception.ConflictException;
import com.netbanking.common.exception.ResourceNotFoundException;

import jakarta.persistence.criteria.Predicate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class AdminAccountService {
    private final BankAccountRepository accounts;
    private final AccountHolderRepository holders;
    private final AuditLogService audit;

    public AdminAccountService(
            BankAccountRepository accounts,
            AccountHolderRepository holders,
            AuditLogService audit) {
        this.accounts = accounts;
        this.holders = holders;
        this.audit = audit;
    }

    public Page<AdminAccountResponse> search(
            String query, Long customerId, AccountStatus status, int page, int size) {
        validatePage(page, size);
        if (customerId != null && customerId <= 0) {
            throw new IllegalArgumentException("Customer identifier must be positive.");
        }
        String normalizedQuery = query == null || query.isBlank() ? null : query.strip();
        Page<BankAccount> result =
                accounts.findAll(
                        specification(normalizedQuery, customerId, status),
                        PageRequest.of(
                                page,
                                size,
                                Sort.by(
                                        Sort.Order.asc("accountNumber"),
                                        Sort.Order.asc("accountId"))));
        Map<Long, List<Long>> customerIdsByAccount = customerIdsByAccount(result.getContent());
        return result.map(
                account ->
                        toResponse(
                                account,
                                customerIdsByAccount.getOrDefault(account.getAccountId(), List.of())));
    }

    @Transactional
    public AdminAccountResponse changeStatus(
            Long administratorUserId, Long accountId, AccountStatus status) {
        if (status == AccountStatus.PENDING) {
            throw new IllegalArgumentException(
                    "Administrators cannot return an account to PENDING status.");
        }
        BankAccount account =
                accounts.findByIdForUpdate(accountId)
                        .orElseThrow(() -> new ResourceNotFoundException("Account was not found."));
        String previousStatus = account.getAccountStatus();
        if (previousStatus.equals(status.name())) {
            throw new ConflictException("Account already has the requested status.");
        }
        account.changeStatus(status.name());
        audit.record(
                administratorUserId,
                "ADMIN_ACCOUNT_STATUS_CHANGED",
                "ACCOUNT",
                String.valueOf(accountId),
                "SUCCESS",
                "from=" + previousStatus + ";to=" + status);
        return toResponse(account, activeCustomerIds(accountId));
    }

    private static Specification<BankAccount> specification(
            String query, Long customerId, AccountStatus status) {
        return (root, criteria, builder) -> {
            Collection<Predicate> predicates = new ArrayList<>();
            if (query != null) {
                String pattern = "%" + escapeLike(query.toLowerCase(Locale.ROOT)) + "%";
                predicates.add(
                        builder.like(builder.lower(root.get("accountNumber")), pattern, '\\'));
            }
            if (status != null) {
                predicates.add(builder.equal(root.get("accountStatus"), status.name()));
            }
            if (customerId != null) {
                var holderAccountIds = criteria.subquery(Long.class);
                var holder = holderAccountIds.from(AccountHolder.class);
                holderAccountIds
                        .select(holder.get("accountId"))
                        .where(
                                builder.equal(holder.get("customerId"), customerId),
                                builder.equal(holder.get("isActive"), "Y"));
                predicates.add(root.get("accountId").in(holderAccountIds));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private Map<Long, List<Long>> customerIdsByAccount(Collection<BankAccount> page) {
        if (page.isEmpty()) return Map.of();
        return holders
                .findByAccountIdInAndIsActive(
                        page.stream().map(BankAccount::getAccountId).toList(), "Y")
                .stream()
                .collect(
                        Collectors.groupingBy(
                                AccountHolder::getAccountId,
                                Collectors.mapping(
                                        AccountHolder::getCustomerId,
                                        Collectors.collectingAndThen(
                                                Collectors.toList(),
                                                ids -> ids.stream().sorted().toList()))));
    }

    private List<Long> activeCustomerIds(Long accountId) {
        return holders.findByAccountIdInAndIsActive(List.of(accountId), "Y").stream()
                .map(AccountHolder::getCustomerId)
                .sorted()
                .toList();
    }

    private static AdminAccountResponse toResponse(
            BankAccount account, List<Long> customerIds) {
        return new AdminAccountResponse(
                account.getAccountId(),
                account.getBranchId(),
                account.getAccountNumber(),
                account.getAccountType(),
                account.getCurrencyCode().trim(),
                account.getAccountStatus(),
                account.getCurrentBalance(),
                account.getAvailableBalance(),
                account.getClosedAt(),
                customerIds);
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
