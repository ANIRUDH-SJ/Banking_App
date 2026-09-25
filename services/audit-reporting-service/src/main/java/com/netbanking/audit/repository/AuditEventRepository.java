package com.netbanking.audit.repository;

import com.netbanking.audit.domain.AuditEvent;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {
    List<AuditEvent> findTop100ByOrderByOccurredAtDesc();
}
