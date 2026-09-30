package com.netbanking.admin.service;

import com.netbanking.admin.api.AuditSearchFilter;
import com.netbanking.audit.domain.AuditEvent;
import com.netbanking.audit.repository.AuditEventRepository;

import jakarta.persistence.criteria.Predicate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class AdminService {
    private final AuditEventRepository repository;

    public AdminService(AuditEventRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Page<AuditEvent> search(AuditSearchFilter filter, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException(
                    "Page must be non-negative and size must be between 1 and 100.");
        }
        return repository.findAll(
                specification(filter),
                PageRequest.of(
                        page,
                        size,
                        Sort.by(
                                Sort.Order.desc("occurredAt"),
                                Sort.Order.desc("auditEventId"))));
    }

    private static Specification<AuditEvent> specification(AuditSearchFilter filter) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filter.userId() != null) {
                predicates.add(builder.equal(root.get("userId"), filter.userId()));
            }
            if (filter.eventType() != null) {
                predicates.add(builder.equal(root.get("eventType"), filter.eventType()));
            }
            if (filter.outcome() != null) {
                predicates.add(builder.equal(root.get("outcome"), filter.outcome().name()));
            }
            if (filter.from() != null) {
                predicates.add(
                        builder.greaterThanOrEqualTo(
                                root.get("occurredAt"), filter.from().atStartOfDay()));
            }
            if (filter.to() != null) {
                LocalDateTime before = filter.to().plusDays(1).atStartOfDay();
                predicates.add(builder.lessThan(root.get("occurredAt"), before));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }
}
