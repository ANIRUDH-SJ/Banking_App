package com.netbanking.user.admin.service;

import com.netbanking.audit.IdentityAuditService;
import com.netbanking.common.exception.ConflictException;
import com.netbanking.common.exception.ResourceNotFoundException;
import com.netbanking.customer.domain.Customer;
import com.netbanking.customer.repository.CustomerRepository;
import com.netbanking.role.domain.Role;
import com.netbanking.user.admin.api.AdminCustomerResponse;
import com.netbanking.user.admin.api.AdminUserResponse;
import com.netbanking.user.domain.AppUser;
import com.netbanking.user.domain.UserStatus;
import com.netbanking.user.repository.AppUserRepository;

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
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class AdminUserService {
    private final AppUserRepository users;
    private final CustomerRepository customers;
    private final IdentityAuditService audit;

    public AdminUserService(
            AppUserRepository users,
            CustomerRepository customers,
            IdentityAuditService audit) {
        this.users = users;
        this.customers = customers;
        this.audit = audit;
    }

    public Page<AdminUserResponse> search(
            String query, UserStatus status, int page, int size) {
        validatePage(page, size);
        String normalizedQuery = query == null || query.isBlank() ? null : query.strip();
        Page<AppUser> result =
                users.findAll(
                        specification(normalizedQuery, status),
                        PageRequest.of(
                                page,
                                size,
                                Sort.by(
                                        Sort.Order.asc("username"),
                                        Sort.Order.asc("userId"))));
        Map<Long, Customer> customerByUserId = customersByUserId(result.getContent());
        return result.map(user -> toResponse(user, customerByUserId.get(user.getUserId())));
    }

    @Transactional
    public AdminUserResponse changeStatus(
            Long administratorUserId, Long targetUserId, UserStatus status) {
        if (Objects.equals(administratorUserId, targetUserId)) {
            throw new ConflictException("Administrators cannot change their own status.");
        }
        AppUser user =
                users.findByIdForUpdate(targetUserId)
                        .orElseThrow(() -> new ResourceNotFoundException("User was not found."));
        UserStatus previousStatus = user.getAccountStatus();
        if (previousStatus == status) {
            throw new ConflictException("User already has the requested status.");
        }
        user.changeAdministrativeStatus(status);
        audit.success(
                administratorUserId,
                "ADMIN_USER_STATUS_CHANGED",
                "USER",
                String.valueOf(targetUserId),
                "from=" + previousStatus + ";to=" + status);
        return toResponse(user, customers.findByUserId(targetUserId).orElse(null));
    }

    private static Specification<AppUser> specification(String query, UserStatus status) {
        return (root, ignored, builder) -> {
            Collection<Predicate> predicates = new ArrayList<>();
            if (query != null) {
                String pattern = "%" + escapeLike(query.toLowerCase(Locale.ROOT)) + "%";
                predicates.add(
                        builder.or(
                                builder.like(builder.lower(root.get("username")), pattern, '\\'),
                                builder.like(builder.lower(root.get("email")), pattern, '\\')));
            }
            if (status != null) {
                predicates.add(builder.equal(root.get("accountStatus"), status));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private Map<Long, Customer> customersByUserId(Collection<AppUser> page) {
        if (page.isEmpty()) return Map.of();
        return customers
                .findByUserIdIn(page.stream().map(AppUser::getUserId).toList())
                .stream()
                .collect(Collectors.toMap(Customer::getUserId, Function.identity()));
    }

    private static AdminUserResponse toResponse(AppUser user, Customer customer) {
        return new AdminUserResponse(
                user.getUserId(),
                user.getUsername(),
                user.getEmail(),
                user.getAccountStatus().name(),
                user.getFailedLoginAttempts(),
                user.getLockedUntil(),
                user.getLastLoginAt(),
                user.getRoles().stream().map(Role::getRoleCode).sorted().toList(),
                customer == null
                        ? null
                        : new AdminCustomerResponse(
                                customer.getCustomerId(),
                                customer.getCustomerNumber(),
                                customer.getFirstName(),
                                customer.getLastName(),
                                customer.getMobileNumber(),
                                customer.getKycStatus(),
                                "Y".equals(customer.getIsActive())));
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
