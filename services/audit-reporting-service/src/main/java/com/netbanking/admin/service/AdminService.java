package com.netbanking.admin.service;

import com.netbanking.audit.domain.AuditEvent;
import com.netbanking.audit.repository.AuditEventRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AdminService {
    private final AuditEventRepository repository;

    public AdminService(AuditEventRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<AuditEvent> recentAuditEvents() {
        return repository.findTop100ByOrderByOccurredAtDesc();
    }
}
